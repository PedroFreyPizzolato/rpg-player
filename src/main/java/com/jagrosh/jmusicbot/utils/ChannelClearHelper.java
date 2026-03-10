/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.Permission;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared logic for clearing all messages in the guild's configured text channel.
 * Used by both the slash and prefix clearchannel commands.
 */
public final class ChannelClearHelper
{
    private static final int BULK_DELETE_LIMIT = 100;
    private static final long BULK_DELETE_AGE_DAYS = 14;

    private ChannelClearHelper() {}

    /**
     * Callbacks for clear-channel operations. Implement to send replies (slash or prefix).
     */
    public interface ClearChannelCallback
    {
        void onNoChannelConfigured();

        void onNoPermission();

        void onClearingStarted();

        void onCleared(int count);

        void onError(Throwable t);
    }

    /**
     * Callback for purge-only operations (after channel is already resolved).
     */
    public interface PurgeCallback
    {
        void onCleared(int count);

        void onError(Throwable t);
    }

    /**
     * Runs the purge on the given channel. Use this after deferring a slash reply.
     *
     * @param channel  the channel to clear
     * @param callback onCleared(count) or onError(t) when done
     */
    public static void purgeChannel(TextChannel channel, PurgeCallback callback)
    {
        AtomicInteger totalDeleted = new AtomicInteger(0);
        takeAndProcessBatch(channel, totalDeleted, new ClearChannelCallback()
        {
            @Override
            public void onNoChannelConfigured() { /* not used */ }

            @Override
            public void onNoPermission() { /* not used */ }

            @Override
            public void onClearingStarted() { /* not used */ }

            @Override
            public void onCleared(int count)
            {
                callback.onCleared(count);
            }

            @Override
            public void onError(Throwable t)
            {
                callback.onError(t);
            }
        });
    }

    /**
     * Resolves the target channel from settings, checks permissions, then runs the purge asynchronously.
     * Callbacks are invoked on the calling thread for validation errors; onClearingStarted is called
     * before starting the purge, then onCleared(count) or onError(t) when done.
     *
     * @param guild    the guild
     * @param settings the guild settings (e.g. from event.getClient().getSettingsFor(guild))
     * @param callback callbacks for results and progress
     */
    public static void clearConfiguredTextChannel(Guild guild, Settings settings, ClearChannelCallback callback)
    {
        TextChannel channel = settings.getTextChannel(guild);
        if (channel == null)
        {
            callback.onNoChannelConfigured();
            return;
        }

        if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE))
        {
            callback.onNoPermission();
            return;
        }

        callback.onClearingStarted();

        purgeChannel(channel, new PurgeCallback()
        {
            @Override
            public void onCleared(int count)
            {
                callback.onCleared(count);
            }

            @Override
            public void onError(Throwable t)
            {
                callback.onError(t);
            }
        });
    }

    private static void takeAndProcessBatch(TextChannel channel, AtomicInteger totalDeleted, ClearChannelCallback callback)
    {
        channel.getIterableHistory()
                .cache(false)
                .limit(BULK_DELETE_LIMIT)
                .takeAsync(BULK_DELETE_LIMIT)
                .whenComplete((batch, throwable) ->
                {
                    if (throwable != null)
                    {
                        callback.onError(throwable);
                        return;
                    }
                    if (batch == null || batch.isEmpty())
                    {
                        callback.onCleared(totalDeleted.get());
                        return;
                    }

                    final Instant bulkCutoff = Instant.now().minus(BULK_DELETE_AGE_DAYS, ChronoUnit.DAYS);
                    List<Message> recent = new ArrayList<>();
                    List<Message> old = new ArrayList<>();
                    for (Message m : batch)
                    {
                        if (m.getTimeCreated().toInstant().isAfter(bulkCutoff))
                            recent.add(m);
                        else
                            old.add(m);
                    }

                    int batchCount = batch.size();

                    Runnable onBatchDone = () ->
                    {
                        totalDeleted.addAndGet(batchCount);
                        if (batch.size() >= BULK_DELETE_LIMIT)
                            takeAndProcessBatch(channel, totalDeleted, callback);
                        else
                            callback.onCleared(totalDeleted.get());
                    };

                    if (recent.isEmpty() && old.isEmpty())
                    {
                        onBatchDone.run();
                        return;
                    }

                    if (!recent.isEmpty())
                    {
                        List<CompletableFuture<Void>> purgeFutures = channel.purgeMessages(recent);
                        CompletableFuture.allOf(purgeFutures.toArray(new CompletableFuture[0]))
                                .thenRun(() -> deleteOldOneByOne(channel, old, 0, callback, onBatchDone))
                                .exceptionally(t ->
                                {
                                    callback.onError(t);
                                    return null;
                                });
                    }
                    else
                        deleteOldOneByOne(channel, old, 0, callback, onBatchDone);
                });
    }

    private static void deleteOldOneByOne(TextChannel channel, List<Message> old, int index,
                                          ClearChannelCallback callback, Runnable whenDone)
    {
        if (index >= old.size())
        {
            whenDone.run();
            return;
        }
        Message msg = old.get(index);
        msg.delete().queue(
                v -> deleteOldOneByOne(channel, old, index + 1, callback, whenDone),
                t -> callback.onError(t)
        );
    }
}

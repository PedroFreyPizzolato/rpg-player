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
package com.jagrosh.jmusicbot.gui;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.gui.components.StatusBar;
import com.jagrosh.jmusicbot.gui.model.BotStatusData;
import com.jagrosh.jmusicbot.gui.panels.SettingsPanel;
import com.jagrosh.jmusicbot.gui.panels.StatusPanel;
import com.jagrosh.jmusicbot.gui.theme.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;

/**
 * Main application frame for JMusicBot with modern FlatLaf styling.
 * Provides a tabbed interface with console, status, and settings panels.
 *
 * @author Arif Banai (arif-banai)
 */
public class MainFrame extends JFrame {
    
    private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);
    private static final String TITLE = "JMusicBot";
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    
    private final Bot bot;
    private final JTabbedPane tabbedPane;
    private final ConsolePanel consolePanel;
    private final StatusPanel statusPanel;
    private final SettingsPanel settingsPanel;
    private final StatusBar statusBar;
    private final Instant startTime;
    private Timer statusUpdateTimer;
    
    /**
     * Creates the main application frame.
     *
     * @param bot the bot instance
     */
    public MainFrame(Bot bot) {
        super(TITLE);
        this.bot = bot;
        this.startTime = Instant.now();
        
        // Initialize panels
        this.consolePanel = new ConsolePanel();
        this.statusPanel = new StatusPanel(bot);
        this.settingsPanel = new SettingsPanel();
        this.statusBar = new StatusBar();
        this.tabbedPane = new JTabbedPane();
        
        initializeFrame();
    }
    
    /**
     * Initializes the frame layout and components.
     */
    private void initializeFrame() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(600, 400));
        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        
        // Create menu bar
        setJMenuBar(createMenuBar());
        
        // Setup tabbed pane with icons
        setupTabbedPane();
        
        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
        
        // Window close handler
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });
    }
    
    /**
     * Initializes and shows the frame.
     */
    public void init() {
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        
        // Start status bar update timer
        startStatusBarUpdates();
        
        LOG.info("JMusicBot GUI initialized");
    }
    
    /**
     * Starts a timer to periodically update status from bot's JDA.
     * Fetches data once and distributes to both StatusBar and StatusPanel.
     */
    private void startStatusBarUpdates() {
        statusUpdateTimer = new Timer(2000, e -> updateAllStatusDisplays());
        statusUpdateTimer.setInitialDelay(500);
        statusUpdateTimer.start();
    }
    
    /**
     * Fetches status data once and updates both StatusBar and StatusPanel.
     * Single source of truth for status information.
     */
    private void updateAllStatusDisplays() {
        // Fetch data once (includes uptime calculation)
        BotStatusData statusData = BotStatusData.fromBot(bot, startTime);
        
        // Distribute to both components
        statusBar.updateStatus(statusData, statusData.uptimeString());
        statusPanel.updateStatus(statusData);
    }
    
    /**
     * Creates the menu bar with File, View, and Help menus.
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setMnemonic('x');
        exitItem.addActionListener(e -> handleWindowClosing());
        fileMenu.add(exitItem);
        
        // View menu
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic('V');
        
        // Theme submenu
        JMenu themeMenu = new JMenu("Theme");
        ButtonGroup themeGroup = new ButtonGroup();
        
        for (ThemeManager.Theme theme : ThemeManager.getAvailableThemes()) {
            JRadioButtonMenuItem themeItem = new JRadioButtonMenuItem(theme.getDisplayName());
            themeItem.setSelected(theme == ThemeManager.getCurrentTheme());
            themeItem.addActionListener(e -> ThemeManager.setTheme(theme));
            themeGroup.add(themeItem);
            themeMenu.add(themeItem);
        }
        viewMenu.add(themeMenu);
        
        viewMenu.addSeparator();
        
        // Tab navigation
        JMenuItem consoleTab = new JMenuItem("Console");
        consoleTab.addActionListener(e -> tabbedPane.setSelectedIndex(0));
        viewMenu.add(consoleTab);
        
        JMenuItem statusTab = new JMenuItem("Status");
        statusTab.addActionListener(e -> tabbedPane.setSelectedIndex(1));
        viewMenu.add(statusTab);
        
        JMenuItem settingsTab = new JMenuItem("Settings");
        settingsTab.addActionListener(e -> tabbedPane.setSelectedIndex(2));
        viewMenu.add(settingsTab);
        
        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic('H');
        
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        
        return menuBar;
    }
    
    /**
     * Sets up the tabbed pane with all panels.
     */
    private void setupTabbedPane() {
        tabbedPane.setTabPlacement(JTabbedPane.TOP);
        
        // Add tabs with tooltips
        tabbedPane.addTab("Console", null, consolePanel, "View application logs and output");
        tabbedPane.addTab("Status", null, statusPanel, "View bot status and connected guilds");
        tabbedPane.addTab("Settings", null, settingsPanel, "Configure GUI settings");
    }
    
    /**
     * Handles window closing - shuts down the bot gracefully.
     */
    private void handleWindowClosing() {
        int result = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to exit? This will shut down the bot.",
            "Confirm Exit",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            LOG.info("User requested shutdown");
            try {
                bot.shutdown();
            } catch (Exception ex) {
                LOG.error("Error during shutdown", ex);
            }
            dispose();
            System.exit(0);
        }
    }
    
    /**
     * Shows the About dialog.
     */
    private void showAboutDialog() {
        String message = """
            JMusicBot
            
            A cross-platform Discord music bot with a clean interface.
            
            Theme: %s
            Java: %s
            """.formatted(
                ThemeManager.getCurrentTheme().getDisplayName(),
                System.getProperty("java.version")
            );
        
        JOptionPane.showMessageDialog(
            this,
            message,
            "About JMusicBot",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
    
    
    /**
     * Gets the status panel for external updates.
     */
    public StatusPanel getStatusPanel() {
        return statusPanel;
    }
    
    /**
     * Gets the console panel.
     */
    public ConsolePanel getConsolePanel() {
        return consolePanel;
    }
}

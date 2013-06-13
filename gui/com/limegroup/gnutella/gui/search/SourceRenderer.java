package com.limegroup.gnutella.gui.search;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;

import com.frostwire.gui.LocaleLabel;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIMediator;

public class SourceRenderer extends JPanel implements TableCellRenderer {

    private SourceHolder sourceHolder;
    private JLabel sourceIcon;
    private JLabel sourceLabel;
    
    private final Map<String,ImageIcon> sourceIcons;
    
    public SourceRenderer() {
        super();
        sourceIcons = new HashMap<>();
        initIcons();
        initUI();
    }
    
    private void initIcons() {
        System.out.println("SourceRenderer.initIcons again.");
        sourceIcons.put("soundcloud", GUIMediator.getThemeImage("soundcloud_off"));
        sourceIcons.put("youtube", GUIMediator.getThemeImage("youtube_on"));
        sourceIcons.put("archive.org", GUIMediator.getThemeImage("archive_source"));
        sourceIcons.put("isohunt", GUIMediator.getThemeImage("isohunt_source"));
        sourceIcons.put("default", GUIMediator.getThemeImage("seeding_small"));
    }

    private void initUI() {
        setLayout(new MigLayout("fillx, insets 5px 5px 0 0, gapx 4px, alignx left",
                "[16px!]3px![left,grow]"));
        sourceIcon = new JLabel();
        final Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
        sourceIcon.setCursor(handCursor);
        sourceLabel = new LocaleLabel();
        sourceLabel.setCursor(handCursor);
        add(sourceIcon,"w 16px!, h 16px!, left");
        add(sourceLabel, "growx, align left");
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int columns) {
        this.setOpaque(true);
        this.setEnabled(true);
        if (isSelected) {
            this.setBackground(ThemeMediator.TABLE_SELECTED_BACKGROUND_ROW_COLOR);
        } else {
            this.setBackground(row % 2 == 1 ? ThemeMediator.TABLE_ALTERNATE_ROW_COLOR : Color.WHITE);
        }
        this.updateUI((SourceHolder) value, table, row);
        return this;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    private void updateUI(SourceHolder value, JTable table, int row) {
        this.sourceHolder = value;
        updateIcon();
        updateLinkLabel(table);
    }

    private void updateIcon() {
        if (getSourceHolder() != null) {
            String sourceName = getSourceHolder().getSourceName().toLowerCase();
            if (sourceName.contains("-")) {
                sourceName = sourceName.substring(0, sourceName.indexOf("-")).trim();
            }
            
            ImageIcon icon = sourceIcons.get(sourceName);
            if (icon != null) {
                sourceIcon.setIcon(icon);
            } else {
                System.out.println("no icon for " + sourceName);
                sourceIcon.setIcon(sourceIcons.get("default"));
            }
        }
    }
    
    private void updateLinkLabel(JTable table) {
        if (getSourceHolder() != null) {
            sourceLabel.setText(getSourceHolder().getSourceNameHTML());
            syncFont(table, sourceLabel);
        }
    }
    
    private void syncFont(JTable table, JComponent c) {
        Font tableFont = table.getFont();
        if (tableFont != null && !tableFont.equals(c.getFont())) {
            c.setFont(tableFont);
        }
    }
    
    private SourceHolder getSourceHolder() {
        return this.sourceHolder;
    }
}

package com.liubs.aicommit.settings.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;

/** Native combo editor with Enter-to-add and a flat, individually removable model list. */
final class ModelSelector extends JPanel {
    private JPopupMenu popup;
    private boolean refreshingCombo;
    private final ComboBox<String> combo = new ComboBox<String>() {
        @Override
        public void setPopupVisible(boolean visible) {
            if (refreshingCombo) {
                return;
            }
            if (visible) {
                showModels();
            } else {
                hideModels();
            }
        }

        @Override
        public boolean isPopupVisible() {
            return popup != null && popup.isVisible();
        }
    };
    private final JBTextField input;
    private final List<String> models = new ArrayList<>();
    private String selectedModel = "";
    private final MouseAdapter popupMouseHandler = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent event) {
            if (combo.isEnabled() && SwingUtilities.isLeftMouseButton(event)) {
                combo.requestFocusInWindow();
                combo.setPopupVisible(!combo.isPopupVisible());
                event.consume();
            }
        }
    };

    ModelSelector() {
        super(new BorderLayout());
        combo.setEditable(true);
        combo.setEditor(new BasicComboBoxEditor() {
            @Override
            protected JTextField createEditorComponent() {
                JBTextField field = new JBTextField();
                field.setBorder(JBUI.Borders.empty(0, 4));
                return field;
            }
        });
        input = (JBTextField) combo.getEditor().getEditorComponent();
        add(combo, BorderLayout.CENTER);
        input.getAccessibleContext().setAccessibleName("Model ID");
        combo.getAccessibleContext().setAccessibleName("Model");
        // Consume Enter here so it never activates the settings dialog's Save button.
        bind(input, KeyEvent.VK_ENTER, "addModel", () -> {
            String model = input.getText().trim();
            if (model.isEmpty()) {
                return;
            }
            if (!models.contains(model)) {
                models.add(model);
            }
            selectedModel = model;
            refreshCombo("");
            hideModels();
        });
        bind(input, KeyEvent.VK_DOWN, "showModels", this::showModels);
        bind(input, KeyEvent.VK_UP, "showModels", this::showModels);
        bind(combo, KeyEvent.VK_DOWN, "showModels", this::showModels);
        bind(combo, KeyEvent.VK_UP, "showModels", this::showModels);
        bind(combo, KeyEvent.VK_ENTER, "showModels", this::showModels);
        // The native arrow's mouse listener calls ComboPopup.show() directly,
        // bypassing setPopupVisible(). Route it to the same popup as the keyboard.
        combo.addPropertyChangeListener("UI", event -> installPopupMouseHandler());
        installPopupMouseHandler();
        updateHint();
    }

    private void installPopupMouseHandler() {
        ComboPopup nativePopup = combo.getPopup();
        if (nativePopup != null) {
            replacePopupMouseHandler(combo, nativePopup.getMouseListener(), nativePopup.getMouseMotionListener());
        }
    }

    private void replacePopupMouseHandler(Component component, MouseListener mouse, MouseMotionListener motion) {
        boolean handlesPopup = false;
        for (MouseListener listener : component.getMouseListeners()) {
            if (listener == mouse || listener == popupMouseHandler) {
                component.removeMouseListener(listener);
                handlesPopup = true;
            }
        }
        component.removeMouseMotionListener(motion);
        if (handlesPopup) {
            component.addMouseListener(popupMouseHandler);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                replacePopupMouseHandler(child, mouse, motion);
            }
        }
    }

    void setModels(List<String> values, String selected) {
        hideModels();
        models.clear();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !models.contains(value.trim())) {
                models.add(value.trim());
            }
        }
        selectedModel = selected == null ? "" : selected.trim();
        refreshCombo(selectedModel);
    }

    List<String> getModels() {
        return new ArrayList<>(models);
    }

    String getSelectedModel() {
        String text = combo.isEditable() ? input.getText().trim() : "";
        return text.isEmpty() ? selectedModel : text;
    }

    void setEditable(boolean editable) {
        combo.setEditable(editable);
        input.setEditable(editable);
        updateHint();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        combo.setEnabled(enabled);
        input.setEnabled(enabled);
        if (!enabled) {
            hideModels();
        }
    }

    private void refreshCombo(String editorText) {
        // Updating the native combo model must not dismiss our popup during deletion.
        refreshingCombo = true;
        try {
            combo.setModel(new DefaultComboBoxModel<>(models.toArray(new String[0])));
            combo.setSelectedItem(selectedModel.isEmpty() ? null : selectedModel);
            input.setText(editorText);
        } finally {
            refreshingCombo = false;
        }
        updateHint();
    }

    private void updateHint() {
        input.getEmptyText().setText(selectedModel.isEmpty()
                ? "Type a model ID and press Enter" : "Current: " + selectedModel);
        String hint = combo.isEditable()
                ? "Press Enter to add, then type the next ID. Current: " + (selectedModel.isEmpty() ? "none" : selectedModel)
                : "Select a model from the dropdown";
        combo.setToolTipText(hint);
        input.setToolTipText(hint);
    }

    private void hideModels() {
        if (popup != null) {
            popup.setVisible(false);
        }
        if (combo != null) {
            combo.repaint();
        }
    }

    private void showModels() {
        if (!isEnabled()) {
            return;
        }
        hideModels();
        JBList<String> list = createModelList();
        popup = new JPopupMenu();
        popup.setBorder(JBUI.Borders.customLine(JBColor.border()));
        JBScrollPane scroll = new JBScrollPane(list);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setHorizontalScrollBarPolicy(JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // Include the popup border in the width so its edges align with the combo.
        int borderWidth = popup.getInsets().left + popup.getInsets().right;
        scroll.setPreferredSize(new Dimension(Math.max(1, combo.getWidth() - borderWidth),
                list.getFixedCellHeight() * Math.max(1, Math.min(models.size(), 8))));
        popup.add(scroll);
        popup.show(combo, 0, combo.getHeight());
        list.ensureIndexIsVisible(list.getSelectedIndex());
        SwingUtilities.invokeLater(list::requestFocusInWindow);
    }

    private JBList<String> createModelList() {
        DefaultListModel<String> items = new DefaultListModel<>();
        models.forEach(items::addElement);
        JBList<String> list = new JBList<String>(items) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        list.setExpandableItemsEnabled(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(Math.max(list.getFontMetrics(list.getFont()).getHeight(), JBUI.scale(16)) + JBUI.scale(8));
        list.setBackground(UIUtil.getListBackground());
        list.getEmptyText().setText("No models available");
        list.setCellRenderer((owner, model, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout());
            row.setBorder(JBUI.Borders.empty(0, 6));
            Color foreground = selected ? UIUtil.getListSelectionForeground(true) : UIUtil.getListForeground();
            row.setBackground(selected ? UIUtil.getListSelectionBackground(true) : UIUtil.getListBackground());
            JBLabel name = new JBLabel(model);
            name.setFont(owner.getFont());
            name.setForeground(foreground);
            name.setIcon(model.equals(selectedModel) ? AllIcons.Actions.Checked : EmptyIcon.create(AllIcons.Actions.Checked));
            name.setIconTextGap(JBUI.scale(6));
            JBLabel remove = new JBLabel(AllIcons.Actions.GC);
            remove.setHorizontalAlignment(JBLabel.CENTER);
            remove.setPreferredSize(new Dimension(JBUI.scale(24), list.getFixedCellHeight()));
            row.add(name, BorderLayout.CENTER);
            row.add(remove, BorderLayout.EAST);
            return row;
        });
        list.setSelectedIndex(models.isEmpty() ? -1 : Math.max(0, models.indexOf(selectedModel)));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int index = rowAt(list, event.getPoint());
                if (index >= 0) {
                    list.setSelectedIndex(index);
                    list.setToolTipText(isDeleteTarget(list, index, event.getPoint())
                            ? "Delete " + items.get(index) : items.get(index));
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                int index = rowAt(list, event.getPoint());
                if (index < 0) {
                    return;
                }
                if (isDeleteTarget(list, index, event.getPoint())) {
                    deleteModel(list, items, index);
                } else {
                    chooseModel(items.get(index));
                }
            }
        };
        list.addMouseMotionListener(mouse);
        list.addMouseListener(mouse);
        bind(list, KeyEvent.VK_ENTER, "chooseModel", () -> {
            if (list.getSelectedValue() != null) {
                chooseModel(list.getSelectedValue());
            }
        });
        bind(list, KeyEvent.VK_DELETE, "deleteModel", () -> deleteModel(list, items, list.getSelectedIndex()));
        bind(list, KeyEvent.VK_ESCAPE, "closeModels", () -> {
            hideModels();
            combo.requestFocusInWindow();
        });
        return list;
    }

    private void chooseModel(String model) {
        selectedModel = model;
        refreshCombo(model);
        hideModels();
        combo.requestFocusInWindow();
    }

    private void deleteModel(JBList<String> list, DefaultListModel<String> items, int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        String model = items.get(index);
        String editorText = input.getText();
        models.remove(model);
        if (selectedModel.equals(model)) {
            selectedModel = "";
        }
        if (editorText.trim().equals(model)) {
            editorText = "";
        }
        refreshCombo(editorText);
        items.remove(index);
        list.setSelectedIndex(items.isEmpty() ? -1 : Math.min(index, items.size() - 1));
        if (items.isEmpty()) {
            hideModels();
            combo.requestFocusInWindow();
        } else {
            list.ensureIndexIsVisible(list.getSelectedIndex());
            list.repaint();
        }
    }

    private static int rowAt(JList<?> list, Point point) {
        int index = list.locationToIndex(point);
        Rectangle bounds = index < 0 ? null : list.getCellBounds(index, index);
        return bounds != null && bounds.contains(point) ? index : -1;
    }

    private static boolean isDeleteTarget(JList<?> list, int index, Point point) {
        Rectangle bounds = list.getCellBounds(index, index);
        return point.x >= bounds.x + bounds.width - JBUI.scale(30);
    }

    private static void bind(JComponent component, int key, String name, Runnable action) {
        component.getInputMap().put(KeyStroke.getKeyStroke(key, 0), name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }
}

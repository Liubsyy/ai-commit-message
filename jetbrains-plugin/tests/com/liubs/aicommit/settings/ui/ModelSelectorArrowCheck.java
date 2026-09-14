package com.liubs.aicommit.settings.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;

import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Regression for real arrow mouse events bypassing ComboBox.setPopupVisible. */
public class ModelSelectorArrowCheck {
    private static final class OpenPopup extends JPopupMenu {
        private boolean open = true;
        private int closeCount;

        @Override
        public boolean isVisible() {
            return open;
        }

        @Override
        public void setVisible(boolean visible) {
            open = visible;
            if (!visible) {
                closeCount++;
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void layout(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }

    private static ComboBox<?> combo(ModelSelector selector) throws Exception {
        Field field = ModelSelector.class.getDeclaredField("combo");
        field.setAccessible(true);
        return (ComboBox<?>) field.get(selector);
    }

    private static JButton arrow(ComboBox<?> combo) {
        for (Component component : combo.getComponents()) {
            if (component instanceof JButton) {
                return (JButton) component;
            }
        }
        throw new AssertionError("Native combo arrow button missing");
    }

    private static void verifyClick(ModelSelector selector, boolean arrowClick, String label) throws Exception {
        ComboBox<?> combo = combo(selector);
        selector.setSize(300, 32);
        layout(selector);

        OpenPopup openPopup = new OpenPopup();
        Field popupField = ModelSelector.class.getDeclaredField("popup");
        popupField.setAccessible(true);
        popupField.set(selector, openPopup);
        ComboPopup nativePopup = combo.getPopup();
        check(nativePopup != null, label + ": native popup must be discoverable");
        check(!nativePopup.isVisible(), label + ": native popup initially hidden");
        int[] nativeOpens = {0};
        PopupMenuListener listener = new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) { nativeOpens[0]++; }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }
            public void popupMenuCanceled(PopupMenuEvent event) { }
        };
        combo.addPopupMenuListener(listener);
        Component target = arrowClick ? arrow(combo) : combo;
        long now = System.currentTimeMillis();
        try {
            // Dispatch through the actual native component, not the custom action map.
            target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, now,
                    MouseEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1));
            target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, now + 1,
                    0, 5, 5, 1, false, MouseEvent.BUTTON1));
        } finally {
            combo.removePopupMenuListener(listener);
        }
        check(openPopup.closeCount == 1, label + ": click must close the custom popup exactly once");
        check(!openPopup.isVisible(), label + ": custom popup closed");
        check(nativeOpens[0] == 0, label + ": native plain popup must never open");
        check(!nativePopup.isVisible(), label + ": native plain popup remains hidden");
    }

    public static void main(String[] args) throws Exception {
        boolean dark = args.length > 0 && "dark".equals(args[0]);
        UIManager.setLookAndFeel(dark
                ? new com.intellij.ide.ui.laf.darcula.DarculaLaf()
                : new com.intellij.ide.ui.laf.IntelliJLaf());
        JBColor.setDark(dark);
        SwingUtilities.invokeAndWait(() -> {
            try {
                ModelSelector selector = new ModelSelector();
                selector.setModels(Arrays.asList("first", "second"), "first");
                verifyClick(selector, true, "editable arrow");
                for (int i = 0; i < 3; i++) {
                    SwingUtilities.updateComponentTreeUI(selector);
                    verifyClick(selector, true, "editable arrow after UI refresh " + i);
                }
                selector.setEditable(false);
                verifyClick(selector, true, "managed provider arrow");
                verifyClick(selector, false, "managed provider combo body");
                SwingUtilities.updateComponentTreeUI(selector);
                verifyClick(selector, true, "managed arrow after UI refresh");
                verifyClick(selector, false, "managed combo body after UI refresh");
                selector.setEditable(true);
                verifyClick(selector, true, "editable arrow after restoring editor");
                System.out.println("PASS: actual native arrow/body mouse events route to custom popup; UI refresh preserves routing without duplicate handlers ("
                        + (dark ? "dark" : "light") + ")");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        System.exit(0);
    }
}

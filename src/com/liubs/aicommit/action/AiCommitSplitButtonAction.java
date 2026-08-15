package com.liubs.aicommit.action;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 胶囊分体按钮:左侧 ✦ + 「AI 生成」点击生成,右侧 ∨ 弹出模型菜单。
 * 工具栏原生按钮做不出该样式,故用 CustomComponentAction 自绘。
 */
public class AiCommitSplitButtonAction extends DumbAwareAction implements CustomComponentAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        new GenerateCommitMessageAction().perform(e.getDataContext(), null);
    }

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
        return new SplitButton();
    }

    private static final class SplitButton extends JComponent {

        private static final String TEXT = "AI Generate";

        /** 与左侧相邻组件的间距 */
        private static int leftGap() {
            return JBUI.scale(8);
        }

        private boolean hoverMain;
        private boolean hoverArrow;
        private boolean busy;
        private int spinAngle;
        private final Timer spinnerTimer = new Timer(33, e -> {
            spinAngle = (spinAngle + 10) % 360;
            repaint();
        });

        SplitButton() {
            setOpaque(false);
            setFont(JBUI.Fonts.label());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Generate commit message with AI; click the arrow to switch model");
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean arrow = inArrowZone(e.getX());
                    if (arrow != hoverArrow || arrow == hoverMain) {
                        hoverArrow = arrow;
                        hoverMain = !arrow;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverMain = false;
                    hoverArrow = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (busy || e.getX() < leftGap()) {
                        return;
                    }
                    if (inArrowZone(e.getX())) {
                        showModelPopup();
                    } else {
                        runGenerate();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private void runGenerate() {
            if (busy) {
                return;
            }
            DataContext context = dataContext();
            setBusy(true);
            new GenerateCommitMessageAction().perform(context, () -> setBusy(false));
        }

        private void setBusy(boolean value) {
            busy = value;
            if (value) {
                spinnerTimer.start();
            } else {
                spinnerTimer.stop();
            }
            setCursor(Cursor.getPredefinedCursor(
                    value ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
            repaint();
        }

        private void showModelPopup() {
            ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                    null, new ProviderMenuGroup(), dataContext(),
                    JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
            popup.setShowSubmenuOnHover(true);
            popup.showUnderneathOf(this);
        }

        /**
         * 优先取所在 ActionToolbar 的目标组件上下文(commit 面板会往里塞
         * COMMIT_MESSAGE_CONTROL / COMMIT_WORKFLOW_HANDLER),
         * 直接按组件层级取可能拿不到这些 key。
         */
        private DataContext dataContext() {
            ActionToolbar toolbar = UIUtil.getParentOfType(ActionToolbar.class, this);
            if (toolbar != null) {
                return toolbar.getToolbarDataContext();
            }
            return DataManager.getInstance().getDataContext(this);
        }

        private int arrowZoneWidth() {
            return JBUI.scale(20);
        }

        private boolean inArrowZone(int x) {
            return x >= getWidth() - arrowZoneWidth();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int mainWidth = JBUI.scale(9) + JBUI.scale(15) + JBUI.scale(5)
                    + fm.stringWidth(TEXT) + JBUI.scale(9);
            return new Dimension(leftGap() + mainWidth + arrowZoneWidth(), JBUI.scale(26));
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int gap = leftGap();
                int w = getWidth();
                int h = getHeight();
                int arc = h - JBUI.scale(4);
                Shape pill = new RoundRectangle2D.Float(gap + 0.5f, 0.5f, w - gap - 1f, h - 1f, arc, arc);

                if (hoverMain || hoverArrow) {
                    Shape oldClip = g2.getClip();
                    g2.clip(pill);
                    g2.setColor(JBUI.CurrentTheme.ActionButton.hoverBackground());
                    int hoverSepX = w - arrowZoneWidth();
                    if (hoverArrow) {
                        g2.fillRect(hoverSepX, 0, arrowZoneWidth(), h);
                    } else {
                        g2.fillRect(gap, 0, hoverSepX - gap, h);
                    }
                    g2.setClip(oldClip);
                }

                g2.setColor(JBColor.border());
                g2.draw(pill);
                int sepX = w - arrowZoneWidth();
                g2.drawLine(sepX, JBUI.scale(5), sepX, h - JBUI.scale(5) - 1);

                if (busy) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
                }
                g2.setColor(UIUtil.getLabelForeground());
                int cy = h / 2;
                int iconCx = gap + JBUI.scale(9) + JBUI.scale(7);
                if (busy) {
                    paintSpinner(g2, iconCx, cy);
                } else {
                    paintSparkles(g2, iconCx, cy);
                }

                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int textX = gap + JBUI.scale(9) + JBUI.scale(15) + JBUI.scale(5);
                int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(TEXT, textX, textY);

                paintChevron(g2, sepX + arrowZoneWidth() / 2, cy);
            } finally {
                g2.dispose();
            }
        }

        /** 生成中的加载动画:旋转的 270° 圆弧 */
        private void paintSpinner(Graphics2D g2, int cx, int cy) {
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int r = JBUI.scale(5);
            g2.drawArc(cx - r, cy - r, r * 2, r * 2, -spinAngle, 270);
        }

        private static void paintSparkles(Graphics2D g2, int cx, int cy) {
            g2.fill(star(cx - JBUI.scale(1), cy + JBUI.scale(1), JBUI.scale(6), JBUI.scale(6) * 0.36));
            g2.fill(star(cx + JBUI.scale(5), cy - JBUI.scale(4), JBUIScale.scale(2.6f), JBUIScale.scale(2.6f) * 0.4));
        }

        /** 四角星:8 个顶点交替使用外径/内径 */
        private static Path2D star(double cx, double cy, double outer, double inner) {
            Path2D path = new Path2D.Double();
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI / 4 * i - Math.PI / 2;
                double radius = i % 2 == 0 ? outer : inner;
                double x = cx + Math.cos(angle) * radius;
                double y = cy + Math.sin(angle) * radius;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.closePath();
            return path;
        }

        private static void paintChevron(Graphics2D g2, int cx, int cy) {
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D chevron = new Path2D.Float();
            float half = JBUIScale.scale(3.4f);
            chevron.moveTo(cx - half, cy - JBUIScale.scale(1.6f));
            chevron.lineTo(cx, cy + JBUIScale.scale(1.9f));
            chevron.lineTo(cx + half, cy - JBUIScale.scale(1.6f));
            g2.draw(chevron);
        }
    }
}

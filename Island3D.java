import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Island3D - Eine 3D Insel mit First-Person-Perspektive
 * Für BlueJ geeignet (pure Java, kein externes Framework)
 *
 * Steuerung:
 *   W / Pfeil hoch   = Vorwärts
 *   S / Pfeil runter = Rückwärts
 *   A / Pfeil links  = Links drehen
 *   D / Pfeil rechts = Rechts drehen
 *   Maus ziehen      = Umsehen (horizontal UND vertikal)
 */
public class Island3D extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Island3D());
    }

    private GamePanel gamePanel;

    boolean fullscreen = false;
    Dimension windowedSize;
    Point     windowedPos;

    public Island3D() {
        setTitle("🏝  3D Insel – First Person  [F11 = Vollbild]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        gamePanel = new GamePanel(this);
        add(gamePanel);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        gamePanel.startLoop();
    }

    void toggleFullscreen() {
        GraphicsDevice gd = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .getDefaultScreenDevice();

        if (!fullscreen) {
            windowedSize = getSize();
            windowedPos  = getLocation();
            dispose();
            setUndecorated(true);
            setVisible(true);
            gd.setFullScreenWindow(this);
        } else {
            gd.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            setSize(windowedSize);
            setLocation(windowedPos);
            setVisible(true);
        }
        fullscreen = !fullscreen;
        gamePanel.requestFocusInWindow();
    }

    // ══════════════════════════════════════════════
    //  GAME PANEL
    // ══════════════════════════════════════════════
    static class GamePanel extends JPanel implements KeyListener {

        static final int W = 900, H = 600;

        Island3D frame;

        // ── Startmenü ─────────────────────────────
        // Zustände: "MENU" = Hauptmenü, "HELP" = Anleitungsseite, "GAME" = Spielen
        String gameState = "MENU";

        // Menü-Button-Positionen (für Klick-Erkennung)
        Rectangle btnPlay   = new Rectangle();
        Rectangle btnHelp   = new Rectangle();
        Rectangle btnBack   = new Rectangle();

        // Animations-Tick für Menü
        int menuTick = 0;

        // ── Kamera / Spieler ──────────────────────
        double camX = 0, camY = 1.65, camZ = 0;
        double yaw   = 0;
        double pitch = 0;
        final double MOVE_SPEED = 0.15;
        final double TURN_SPEED = 2.5;

        boolean keyW, keyS, keyA, keyD;

        int lastMouseX = -1;
        int lastMouseY = -1;
        double mouseSens = 0.25;
        final double PITCH_MIN = -89.0;
        final double PITCH_MAX =  89.0;

        // ── Welt ──────────────────────────────────
        static final int GRID = 60;
        float[][] heightMap;
        Color[][] terrainColor;

        List<Triangle> triangles      = new ArrayList<>();
        List<Triangle> waterTriangles = new ArrayList<>();
        List<Triangle> shrubTriangles = new ArrayList<>();
        List<Triangle> treeTriangles  = new ArrayList<>();

        // Baum-Kollision: Stamm-Positionen und -Radien
        static final int MAX_TREES = 60;
        double[] treeColX   = new double[MAX_TREES];
        double[] treeColZ   = new double[MAX_TREES];
        double[] treeColR   = new double[MAX_TREES];
        int      treeColCount = 0;

        // Vollständige Baumdaten für Neuaufbau nach dem Fällen
        double[]  treeWX       = new double[MAX_TREES];
        double[]  treeWY       = new double[MAX_TREES];
        double[]  treeWZ       = new double[MAX_TREES];
        double[]  treeCrownR   = new double[MAX_TREES];
        double[]  treeCrownH   = new double[MAX_TREES];
        double[]  treeTrunkH   = new double[MAX_TREES];
        int[]     treeColorR   = new int[MAX_TREES];
        int[]     treeColorG   = new int[MAX_TREES];
        int[]     treeColorB   = new int[MAX_TREES];
        boolean[] treeAlive    = new boolean[MAX_TREES];

        // Fäll-Feedback
        String chopMsg   = "";
        int    chopTimer = 0;

        double waterTime = 0;
        double bobTime   = 0;   // Schaukel-Animation des gehaltenen Items
        double swingTime = 0;   // Swing beim Hotbar-Wechsel
        int    lastHotbarSelected = -1;

        // ── Hunger / Sättigung ────────────────────
        double saturation          = 100.0;
        static final double SAT_MAX        = 100.0;
        static final double SAT_DRAIN_WALK = 0.016;  // pro Tick beim Laufen
        static final double SAT_DRAIN_IDLE = 0.002;  // pro Tick im Stand
        static final double SAT_JUICE_FILL = 40.0;   // Saft füllt 40 Punkte
        int    hungerBlinkTimer    = 0;
        String hungerMsg           = "";
        int    hungerMsgTimer      = 0;

        static final double SCALE = 1.5;

        BufferedImage buffer;
        Graphics2D    bg;

        Font hudFont   = new Font("Monospaced", Font.BOLD, 13);
        Font titleFont = new Font("SansSerif",  Font.BOLD, 20);

        // ── Hotbar ────────────────────────────────
        static final int HOTBAR_SLOTS = 9;
        int hotbarSelected = 0;

        static final String[] ITEM_NAMES  = {
            "Leer", "Leer", "Leer", "Leer", "Leer", "Leer", "Leer", "Leer", "Leer"
        };
        static final Color[] ITEM_COLORS = {
            new Color(60, 60, 60), new Color(60, 60, 60), new Color(60, 60, 60),
            new Color(60, 60, 60), new Color(60, 60, 60), new Color(60, 60, 60),
            new Color(60, 60, 60), new Color(60, 60, 60), new Color(60, 60, 60),
        };
        static final String[] ITEM_ICONS = {
            "–", "–", "–", "–", "–", "–", "–", "–", "–"
        };

        // ── Inventar ──────────────────────────────
        static final String[] MASTER_NAMES  = {
            "Holz", "Stein", "Sand", "Gras", "Wasser",
            "Strauch", "Blume", "Fackel", "Beere", "Stock", "Stein",
            "Axt", "Beerensaft", "Leer"
        };
        static final Color[] MASTER_COLORS = {
            new Color(139,  90,  43),
            new Color(140, 140, 140),
            new Color(238, 214, 175),
            new Color( 86, 155,  56),
            new Color( 30, 100, 200),
            new Color( 50, 130,  40),
            new Color(255,  80, 120),
            new Color(255, 180,  40),
            new Color(200,  20,  20),
            new Color(120,  75,  30),
            new Color(160, 160, 155),
            new Color(180,  90,  20),
            new Color(180,  30,  80),
            new Color( 60,  60,  60),
        };
        static final String[] MASTER_ICONS = {
            "H", "S", "Sa", "G", "W", "Str", "Bl", "F", "●", "/", "▪",
            "⚒", "🍹", "–"
        };
        static final int ITEM_BEERE    = 8;
        static final int ITEM_STOCK    = 9;
        static final int ITEM_STEIN    = 10;
        static final int ITEM_AXT      = 11;
        static final int ITEM_SAFT     = 12;

        static final int INV_ROWS = 4;
        static final int INV_COLS = 9;
        int[] invSlots  = new int[INV_ROWS * INV_COLS];
        int[] hotbarItems = new int[HOTBAR_SLOTS];
        {
            java.util.Arrays.fill(invSlots,    -1);
            java.util.Arrays.fill(hotbarItems, -1);
        }

        boolean inventoryOpen = false;

        int dragSource  = -1;
        int dragItem    = -1;
        int dragMouseX  = 0;
        int dragMouseY  = 0;

        static final int MAX_SHRUBS  = 120;
        static final int BERRIES_PER = 5;
        double[] berryX   = new double[MAX_SHRUBS * BERRIES_PER];
        double[] berryY   = new double[MAX_SHRUBS * BERRIES_PER];
        double[] berryZ   = new double[MAX_SHRUBS * BERRIES_PER];
        boolean[] berryAlive = new boolean[MAX_SHRUBS * BERRIES_PER];
        int berryCount = 0;

        String collectMsg   = "";
        int    collectTimer = 0;

        static final int MAX_GROUND_ITEMS = 300;
        double[]  gItemX     = new double[MAX_GROUND_ITEMS];
        double[]  gItemY     = new double[MAX_GROUND_ITEMS];
        double[]  gItemZ     = new double[MAX_GROUND_ITEMS];
        int[]     gItemType  = new int[MAX_GROUND_ITEMS];
        boolean[] gItemAlive = new boolean[MAX_GROUND_ITEMS];
        int       gItemCount = 0;

        boolean craftingOpen    = false;
        int     craftSelected   = 0;
        String  craftFeedback   = "";
        int     craftFeedTimer  = 0;

        static final String[]   RECIPE_NAMES  = { "Axt", "Beerensaft" };
        static final String[]   RECIPE_DESC   = {
            "Ein einfaches Werkzeug\nzum Hacken von Holz.",
            "Erfrischender Saft\naus frischen Beeren."
        };
        static final int[]      RECIPE_RESULT = { ITEM_AXT, ITEM_SAFT };
        static final int[][][] RECIPE_INGR = {
            { {ITEM_STOCK, 1}, {ITEM_STEIN, 2} },
            { {ITEM_BEERE, 3} }
        };

        int craftScrollY = 0;

        // ──────────────────────────────────────────
        GamePanel(Island3D frame) {
            this.frame = frame;
            setPreferredSize(new Dimension(W, H));
            setFocusable(true);
            addKeyListener(this);

            buffer = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            bg     = buffer.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            bg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            buildWorld();

            addMouseWheelListener(e -> {
                if (gameState.equals("GAME"))
                    hotbarSelected = (hotbarSelected + (int)Math.signum(e.getWheelRotation()) + HOTBAR_SLOTS) % HOTBAR_SLOTS;
            });

            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    int mx = e.getX() * W / Math.max(1, getWidth());
                    int my = e.getY() * H / Math.max(1, getHeight());

                    // ── Startmenü-Klicks ──
                    if (gameState.equals("MENU")) {
                        if (btnPlay.contains(mx, my)) {
                            gameState = "GAME";
                            lastMouseX = -1; lastMouseY = -1;
                        } else if (btnHelp.contains(mx, my)) {
                            gameState = "HELP";
                        }
                        return;
                    }
                    if (gameState.equals("HELP")) {
                        if (btnBack.contains(mx, my)) gameState = "MENU";
                        return;
                    }

                    // ── Spielmodus-Klicks ──
                    if (craftingOpen) {
                        if (mx >= craftBtnX && mx < craftBtnX + craftBtnW &&
                            my >= craftBtnY && my < craftBtnY + craftBtnH) {
                            tryCraft(craftSelected);
                            return;
                        }
                        int bW = 700, bH = 440;
                        int bX = (W - bW) / 2, bY = (H - bH) / 2;
                        int lX = bX + 18, lY = bY + 18;
                        int lW = bW / 2 - 30;
                        int entryH = 52, entryGap = 6, listY = lY + 34;
                        for (int r = 0; r < RECIPE_NAMES.length; r++) {
                            int ey = listY + r * (entryH + entryGap);
                            if (mx >= lX && mx < lX + lW && my >= ey && my < ey + entryH) {
                                craftSelected = r; return;
                            }
                        }
                        return;
                    }

                    if (inventoryOpen) {
                        dragMouseX = mx; dragMouseY = my;
                        int slot = getSlotAt(mx, my);
                        if (slot < 0) return;
                        if (dragSource < 0) {
                            int item = getItemAt(slot);
                            if (item >= 0) { dragSource = slot; dragItem = item; setItemAt(slot, -1); }
                        } else {
                            int targetItem = getItemAt(slot);
                            setItemAt(slot, dragItem);
                            if (targetItem >= 0) { dragSource = slot; dragItem = targetItem; }
                            else { dragSource = -1; dragItem = -1; }
                        }
                    } else {
                        if (!inventoryOpen && !craftingOpen) {
                            if (e.getButton() == MouseEvent.BUTTON1) {
                                // Linksklick → Baum fällen
                                chopNearbyTree();
                            } else if (e.getButton() == MouseEvent.BUTTON3) {
                                // Rechtsklick → Item benutzen (z.B. Saft trinken)
                                drinkJuice();
                            }
                        }
                        lastMouseX = -1; lastMouseY = -1;
                    }
                }
                public void mouseReleased(MouseEvent e) {
                    if (gameState.equals("GAME") && !inventoryOpen) lastMouseX = -1;
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    if (!gameState.equals("GAME")) return;
                    int mx = e.getX() * W / Math.max(1, getWidth());
                    int my = e.getY() * H / Math.max(1, getHeight());
                    if (inventoryOpen) {
                        dragMouseX = mx; dragMouseY = my;
                    } else {
                        if (lastMouseX >= 0 && lastMouseY >= 0) {
                            double dx = e.getX() - lastMouseX;
                            double dy = e.getY() - lastMouseY;
                            yaw   += dx * mouseSens;
                            pitch += dy * mouseSens;
                            if (pitch < PITCH_MIN) pitch = PITCH_MIN;
                            if (pitch > PITCH_MAX) pitch = PITCH_MAX;
                        }
                        lastMouseX = e.getX(); lastMouseY = e.getY();
                    }
                }
                public void mouseMoved(MouseEvent e) {
                    int mx = e.getX() * W / Math.max(1, getWidth());
                    int my = e.getY() * H / Math.max(1, getHeight());
                    dragMouseX = mx; dragMouseY = my;
                    if (gameState.equals("GAME") && !inventoryOpen) {
                        lastMouseX = e.getX(); lastMouseY = e.getY();
                    }
                }
            });
        }

        // ──────────────────────────────────────────
        //  Welt bauen
        // ──────────────────────────────────────────
        void buildWorld() {
            heightMap    = new float[GRID + 1][GRID + 1];
            terrainColor = new Color[GRID + 1][GRID + 1];

            // Die Insel belegt die inneren ~40/60 des Grids
            // Außen: Strand-Ring → dann Ozean
            final double ISLAND_RADIUS = 0.52;  // normalisierter Abstand bis Inselrand
            final double BEACH_RADIUS  = 0.68;  // bis hier: Strand
            final double OCEAN_RADIUS  = 0.72;  // ab hier: tiefer Ozean

            for (int z = 0; z <= GRID; z++) {
                for (int x = 0; x <= GRID; x++) {
                    double nx   = (x - GRID / 2.0) / (GRID / 2.0);
                    double nz   = (z - GRID / 2.0) / (GRID / 2.0);
                    double dist = Math.sqrt(nx * nx + nz * nz);

                    double h;

                    if (dist <= ISLAND_RADIUS) {
                        // ── Insel-Kern (wie bisher, skaliert auf kleineres Gebiet) ──
                        double localDist = dist / ISLAND_RADIUS; // 0..1 innerhalb der Insel
                        h  = 5.5 * Math.exp(-localDist * localDist * 2.0);
                        h += 0.6  * Math.sin(x * 1.3) * Math.cos(z * 1.1);
                        h += 0.3  * Math.sin(x * 2.7 + z * 1.9);
                        h += 0.15 * Math.cos(x * 4.1 - z * 3.3);
                        // Sanftes Abflachen zum Strand hin
                        double edge = Math.max(0, 1.0 - localDist * localDist * 1.6);
                        h *= edge;
                        h = Math.max(h, 0.08); // mind. Strandhöhe am Inselrand

                    } else if (dist <= BEACH_RADIUS) {
                        // ── Strand-Ring: flach, sandig ──
                        double t = (dist - ISLAND_RADIUS) / (BEACH_RADIUS - ISLAND_RADIUS); // 0..1
                        // Strand liegt leicht über Wasser, fällt sanft ins Meer ab
                        h = 0.12 * (1.0 - t) + (-0.3) * t;
                        // Kleine Wellen-/Sanddünen-Variation
                        h += 0.03 * Math.sin(x * 2.1 + z * 1.7);

                    } else {
                        // ── Ozean: tief unter Wasser ──
                        double t = Math.min(1.0, (dist - OCEAN_RADIUS) / 0.15);
                        h = -1.5 - t * 1.5; // bis -3.0 in der Tiefe
                        // Leichte Meeresbodentextur
                        h += 0.08 * Math.sin(x * 0.9 + z * 1.3);
                    }

                    heightMap[z][x] = (float) h;

                    // Farbe
                    if      (dist > BEACH_RADIUS)  terrainColor[z][x] = new Color( 30,  65, 130); // Meeresboden
                    else if (dist > ISLAND_RADIUS) terrainColor[z][x] = new Color(238, 214, 175); // Strand
                    else if (h < 0.15)             terrainColor[z][x] = new Color(238, 214, 175); // Strandsand
                    else if (h < 0.9)              terrainColor[z][x] = new Color( 86, 155,  56);
                    else if (h < 1.8)              terrainColor[z][x] = new Color( 62, 120,  38);
                    else if (h < 2.6)              terrainColor[z][x] = new Color(140, 120, 100);
                    else                           terrainColor[z][x] = new Color(240, 240, 250);
                }
            }

            buildTriangles();
            buildShrubs();
            buildTrees();
            buildGroundItems();
        }

        void buildTriangles() {
            triangles.clear();
            waterTriangles.clear();
            double scale = SCALE;
            for (int z = 0; z < GRID; z++) {
                for (int x = 0; x < GRID; x++) {
                    double x0 = (x - GRID / 2.0) * scale, z0 = (z - GRID / 2.0) * scale;
                    double x1 = x0 + scale,               z1 = z0 + scale;
                    float h00 = heightMap[z][x], h10 = heightMap[z][x+1];
                    float h01 = heightMap[z+1][x], h11 = heightMap[z+1][x+1];
                    Color avg1 = avgColor(terrainColor[z][x], terrainColor[z][x+1], terrainColor[z+1][x]);
                    Color avg2 = avgColor(terrainColor[z][x+1], terrainColor[z+1][x+1], terrainColor[z+1][x]);
                    triangles.add(new Triangle(x0,h00,z0, x1,h10,z0, x0,h01,z1, avg1));
                    triangles.add(new Triangle(x1,h10,z0, x1,h11,z1, x0,h01,z1, avg2));

                    // Wasserfarbe: heller bei Strand, dunkler im tiefen Ozean
                    double nx = (x - GRID / 2.0) / (GRID / 2.0);
                    double nz = (z - GRID / 2.0) / (GRID / 2.0);
                    double dist = Math.sqrt(nx*nx + nz*nz);
                    Color wc;
                    if (dist < 0.60) {
                        wc = new Color(40, 130, 210, 190); // flaches Wasser nahe Strand
                    } else if (dist < 0.75) {
                        wc = new Color(25,  95, 185, 200); // mittleres Wasser
                    } else {
                        wc = new Color(10,  55, 140, 210); // tiefer Ozean
                    }
                    waterTriangles.add(new Triangle(x0,0.05,z0, x1,0.05,z0, x0,0.05,z1, wc));
                    waterTriangles.add(new Triangle(x1,0.05,z0, x1,0.05,z1, x0,0.05,z1, wc));
                }
            }
        }

        void buildShrubs() {
            shrubTriangles.clear();
            berryCount = 0;
            Random rng = new Random(42);
            int shrubCount = 120;
            for (int i = 0; i < shrubCount; i++) {
                int gx = rng.nextInt(GRID), gz = rng.nextInt(GRID);
                double h = heightMap[gz][gx];
                if (h < 0.3 || h > 2.0) continue;
                double wx = (gx - GRID / 2.0) * SCALE + rng.nextDouble() * SCALE;
                double wz = (gz - GRID / 2.0) * SCALE + rng.nextDouble() * SCALE;
                double wy = sampleHeight(wx, wz);
                double radius = 0.35 + rng.nextDouble() * 0.35;
                double bushH  = 0.55 + rng.nextDouble() * 0.50;
                int r = 30 + rng.nextInt(50), g = 110 + rng.nextInt(80), b = 20 + rng.nextInt(30);
                Color bushColorDark  = new Color((int)(r*0.7),(int)(g*0.7),(int)(b*0.7));
                Color bushColorLight = new Color(Math.min(255,r+40),Math.min(255,g+50),Math.min(255,b+20));
                addShrub(wx,wy,wz,radius,bushH,new Color(r,g,b),bushColorDark,bushColorLight,rng);
            }
        }

        // ──────────────────────────────────────────
        //  Bäume bauen  (3× Strauch-Größe)
        // ──────────────────────────────────────────
        void buildTrees() {
            treeTriangles.clear();
            treeColCount = 0;
            Random rng = new Random(77);
            int treeCount = 50;

            for (int i = 0; i < treeCount; i++) {
                int gx = rng.nextInt(GRID), gz = rng.nextInt(GRID);
                double h = heightMap[gz][gx];
                if (h < 0.3 || h > 1.8) continue;

                double wx = (gx - GRID / 2.0) * SCALE + rng.nextDouble() * SCALE;
                double wz = (gz - GRID / 2.0) * SCALE + rng.nextDouble() * SCALE;
                double wy = sampleHeight(wx, wz);

                double crownRadius = (0.35 + rng.nextDouble() * 0.35) * 3.0;
                double crownH      = (0.55 + rng.nextDouble() * 0.50) * 4.5;
                double trunkH      = 2.5  + rng.nextDouble() * 1.5;
                double trunkR      = 0.18 + crownRadius * 0.08;

                int cr = 20 + rng.nextInt(35);
                int cg = 90 + rng.nextInt(70);
                int cb = 10 + rng.nextInt(25);

                if (treeColCount < MAX_TREES) {
                    int idx = treeColCount;
                    treeColX[idx]   = wx;
                    treeColZ[idx]   = wz;
                    treeColR[idx]   = trunkR + 1.0;
                    treeWX[idx]     = wx;
                    treeWY[idx]     = wy;
                    treeWZ[idx]     = wz;
                    treeCrownR[idx] = crownRadius;
                    treeCrownH[idx] = crownH;
                    treeTrunkH[idx] = trunkH;
                    treeColorR[idx] = cr;
                    treeColorG[idx] = cg;
                    treeColorB[idx] = cb;
                    treeAlive[idx]  = true;
                    treeColCount++;
                }

                Color crownMid   = new Color(cr, cg, cb);
                Color crownDark  = new Color((int)(cr*0.65),(int)(cg*0.65),(int)(cb*0.65));
                Color crownLight = new Color(Math.min(255,cr+50),Math.min(255,cg+55),Math.min(255,cb+20));
                addTree(wx, wy, wz, crownRadius, crownH, trunkH, crownMid, crownDark, crownLight, rng);
            }
        }

        /** Baut treeTriangles neu — lässt gefällte Bäume weg. */
        void rebuildTreeTriangles() {
            treeTriangles.clear();
            Random rng = new Random(77); // gleicher Seed → gleiche Farben
            // RNG auf gleichen Stand bringen wie in buildTrees
            // (einfacher: Farben aus gespeicherten treeColorR/G/B lesen)
            for (int i = 0; i < treeColCount; i++) {
                if (!treeAlive[i]) continue;
                int cr = treeColorR[i], cg = treeColorG[i], cb = treeColorB[i];
                Color crownMid   = new Color(cr, cg, cb);
                Color crownDark  = new Color((int)(cr*0.65),(int)(cg*0.65),(int)(cb*0.65));
                Color crownLight = new Color(Math.min(255,cr+50),Math.min(255,cg+55),Math.min(255,cb+20));
                addTree(treeWX[i], treeWY[i], treeWZ[i],
                        treeCrownR[i], treeCrownH[i], treeTrunkH[i],
                        crownMid, crownDark, crownLight, rng);
            }
        }

        /**
         * Fällt den nächsten Baum in Reichweite vor dem Spieler.
         * Nur möglich wenn Axt in der Hand.
         */
        /** Trinkt Beerensaft aus dem aktiven Hotbar-Slot und füllt Sättigung auf. */
        void drinkJuice() {
            if (hotbarItems[hotbarSelected] != ITEM_SAFT) return;
            if (saturation >= SAT_MAX) {
                hungerMsg      = "Du bist nicht hungrig.";
                hungerMsgTimer = 80;
                return;
            }
            hotbarItems[hotbarSelected] = -1;
            saturation = Math.min(SAT_MAX, saturation + SAT_JUICE_FILL);
            hungerMsg      = "Mmm! +" + (int)SAT_JUICE_FILL + " Sättigung";
            hungerMsgTimer = 100;
        }

        void chopNearbyTree() {
            final double REACH = 5.5;
            double fwdX = Math.sin(Math.toRadians(yaw));
            double fwdZ = Math.cos(Math.toRadians(yaw));

            int bestIdx = -1;
            double bestD = Double.MAX_VALUE;

            for (int i = 0; i < treeColCount; i++) {
                if (!treeAlive[i]) continue;
                double dx = treeColX[i] - camX;
                double dz = treeColZ[i] - camZ;
                double dist = Math.sqrt(dx*dx + dz*dz);
                if (dist > REACH) continue;
                double dot = (dx/dist)*fwdX + (dz/dist)*fwdZ;
                if (dot < 0.2) continue;
                if (dist < bestD) { bestIdx = i; bestD = dist; }
            }

            // Kein Baum in Reichweite → gar keine Meldung
            if (bestIdx < 0) return;

            // Baum in Reichweite, aber keine Axt in der Hand
            if (hotbarItems[hotbarSelected] != ITEM_AXT) {
                chopMsg   = "Du brauchst eine Axt!";
                chopTimer = 90;
                return;
            }

            // Baum fällen
            treeAlive[bestIdx] = false;
            rebuildTreeTriangles();

            int logs = 2 + (int)(Math.random() * 3);
            for (int k = 0; k < logs; k++) addToInventory(0);

            chopMsg   = "Baum gefällt! +" + logs + " Holz";
            chopTimer = 120;
        }

        /**
         * Zeichnet einen Baum:
         *  – Oktagonaler Stamm (trunkH hoch)
         *  – Kuppelförmige Krone (wie Strauch, aber 3×)
         */
        void addTree(double wx, double wy, double wz,
                     double crownRadius, double crownH, double trunkH,
                     Color mid, Color dark, Color light, Random rng) {

            double trunkBase = wy;
            double trunkTop  = wy + trunkH;

            // ── Stamm: Oktagon-Prism (8 Seiten-Flächen + Deckelfläche) ──
            int tSlices = 8;
            double tr = 0.18 + crownRadius * 0.08;   // Stammradius proportional zur Krone
            Color trunkSide  = new Color(101, 67, 33);
            Color trunkDark  = new Color( 72, 48, 23);
            Color trunkLight = new Color(130, 88, 45);

            for (int s = 0; s < tSlices; s++) {
                double a0 = 2 * Math.PI * s       / tSlices;
                double a1 = 2 * Math.PI * (s + 1) / tSlices;
                double x0b = wx + Math.cos(a0) * tr, z0b = wz + Math.sin(a0) * tr;
                double x1b = wx + Math.cos(a1) * tr, z1b = wz + Math.sin(a1) * tr;

                // Vorderseite der Stammfläche (zwei Dreiecke pro Seite)
                Color face = (s % 2 == 0) ? trunkSide : trunkDark;
                treeTriangles.add(new Triangle(
                    x0b, trunkBase, z0b,  x1b, trunkBase, z1b,  x1b, trunkTop, z1b,  face));
                treeTriangles.add(new Triangle(
                    x0b, trunkBase, z0b,  x1b, trunkTop,  z1b,  x0b, trunkTop, z0b,  face));

                // Stammdeckel-Fächer
                treeTriangles.add(new Triangle(
                    x0b, trunkTop, z0b,  x1b, trunkTop, z1b,  wx, trunkTop, wz,  trunkLight));
            }

            // ── Krone: untere Kuppelhälfte ──
            int cSlices = 10;
            double crownBase = trunkTop;
            double crownMidY = trunkTop + crownH * 0.55;
            double crownTopY = trunkTop + crownH;

            for (int s = 0; s < cSlices; s++) {
                double a0 = 2 * Math.PI * s       / cSlices;
                double a1 = 2 * Math.PI * (s + 1) / cSlices;
                double bx0 = wx + Math.cos(a0) * crownRadius * 0.75;
                double bz0 = wz + Math.sin(a0) * crownRadius * 0.75;
                double bx1 = wx + Math.cos(a1) * crownRadius * 0.75;
                double bz1 = wz + Math.sin(a1) * crownRadius * 0.75;
                double mx0 = wx + Math.cos(a0) * crownRadius;
                double mz0 = wz + Math.sin(a0) * crownRadius;
                double mx1 = wx + Math.cos(a1) * crownRadius;
                double mz1 = wz + Math.sin(a1) * crownRadius;
                treeTriangles.add(new Triangle(bx0,crownBase,bz0, bx1,crownBase,bz1, mx1,crownMidY,mz1, dark));
                treeTriangles.add(new Triangle(bx0,crownBase,bz0, mx1,crownMidY,mz1, mx0,crownMidY,mz0, dark));
            }

            // ── Krone: obere Kuppelhälfte ──
            for (int s = 0; s < cSlices; s++) {
                double a0 = 2 * Math.PI * s       / cSlices;
                double a1 = 2 * Math.PI * (s + 1) / cSlices;
                double mx0 = wx + Math.cos(a0) * crownRadius;
                double mz0 = wz + Math.sin(a0) * crownRadius;
                double mx1 = wx + Math.cos(a1) * crownRadius;
                double mz1 = wz + Math.sin(a1) * crownRadius;
                // Zweite Kronenebene für mehr Volumen
                double mx0s = wx + Math.cos(a0) * crownRadius * 0.55;
                double mz0s = wz + Math.sin(a0) * crownRadius * 0.55;
                double mx1s = wx + Math.cos(a1) * crownRadius * 0.55;
                double mz1s = wz + Math.sin(a1) * crownRadius * 0.55;
                Color faceCol = (s < cSlices / 2) ? light : mid;
                treeTriangles.add(new Triangle(mx0,crownMidY,mz0, mx1,crownMidY,mz1, wx,crownTopY,wz, faceCol));
                // Extra innere Schicht für Tiefenwirkung
                treeTriangles.add(new Triangle(mx0s,crownMidY+crownH*0.15,mz0s,
                                               mx1s,crownMidY+crownH*0.15,mz1s,
                                               wx, crownTopY - crownH*0.1, wz, mid));
            }
        }

        void addShrub(double wx, double wy, double wz,
                      double radius, double bushH,
                      Color mid, Color dark, Color light, Random rng) {
            int slices = 8;
            double baseY = wy, topY = wy + bushH, midY = wy + bushH * 0.55;
            for (int s = 0; s < slices; s++) {
                double a0 = 2*Math.PI*s/slices, a1 = 2*Math.PI*(s+1)/slices;
                double bx0=wx+Math.cos(a0)*radius*0.75, bz0=wz+Math.sin(a0)*radius*0.75;
                double bx1=wx+Math.cos(a1)*radius*0.75, bz1=wz+Math.sin(a1)*radius*0.75;
                double mx0=wx+Math.cos(a0)*radius, mz0=wz+Math.sin(a0)*radius;
                double mx1=wx+Math.cos(a1)*radius, mz1=wz+Math.sin(a1)*radius;
                shrubTriangles.add(new Triangle(bx0,baseY,bz0, bx1,baseY,bz1, mx1,midY,mz1, dark));
                shrubTriangles.add(new Triangle(bx0,baseY,bz0, mx1,midY,mz1, mx0,midY,mz0, dark));
            }
            for (int s = 0; s < slices; s++) {
                double a0=2*Math.PI*s/slices, a1=2*Math.PI*(s+1)/slices;
                double mx0=wx+Math.cos(a0)*radius, mz0=wz+Math.sin(a0)*radius;
                double mx1=wx+Math.cos(a1)*radius, mz1=wz+Math.sin(a1)*radius;
                Color faceColor = (s < slices/2) ? light : mid;
                shrubTriangles.add(new Triangle(mx0,midY,mz0, mx1,midY,mz1, wx,topY,wz, faceColor));
            }
            double sw = 0.06;
            Color trunkColor = new Color(101,67,33);
            shrubTriangles.add(new Triangle(wx-sw,baseY,wz, wx+sw,baseY,wz, wx+sw,baseY-0.25,wz, trunkColor));
            shrubTriangles.add(new Triangle(wx-sw,baseY,wz, wx+sw,baseY-0.25,wz, wx-sw,baseY-0.25,wz, trunkColor));
            int numBerries = 3 + rng.nextInt(3);
            for (int b = 0; b < numBerries && berryCount < berryX.length; b++) {
                double angle  = rng.nextDouble()*2*Math.PI;
                double height = baseY + bushH*(0.35+rng.nextDouble()*0.30);
                double dist   = radius*(0.55+rng.nextDouble()*0.35);
                berryX[berryCount]=wx+Math.cos(angle)*dist;
                berryY[berryCount]=height;
                berryZ[berryCount]=wz+Math.sin(angle)*dist;
                berryAlive[berryCount]=true;
                berryCount++;
            }
        }

        void buildGroundItems() {
            gItemCount = 0;
            Random rng = new Random(99);
            for (int i = 0; i < 80 && gItemCount < MAX_GROUND_ITEMS; i++) {
                int gx=rng.nextInt(GRID), gz=rng.nextInt(GRID);
                double h=heightMap[gz][gx];
                if (h<0.2||h>2.2) continue;
                double wx=(gx-GRID/2.0)*SCALE+rng.nextDouble()*SCALE;
                double wz=(gz-GRID/2.0)*SCALE+rng.nextDouble()*SCALE;
                gItemX[gItemCount]=wx; gItemY[gItemCount]=sampleHeight(wx,wz); gItemZ[gItemCount]=wz;
                gItemType[gItemCount]=ITEM_STOCK; gItemAlive[gItemCount]=true; gItemCount++;
            }
            for (int i = 0; i < 80 && gItemCount < MAX_GROUND_ITEMS; i++) {
                int gx=rng.nextInt(GRID), gz=rng.nextInt(GRID);
                double h=heightMap[gz][gx];
                if (h<0.1||h>3.5) continue;
                double wx=(gx-GRID/2.0)*SCALE+rng.nextDouble()*SCALE;
                double wz=(gz-GRID/2.0)*SCALE+rng.nextDouble()*SCALE;
                gItemX[gItemCount]=wx; gItemY[gItemCount]=sampleHeight(wx,wz); gItemZ[gItemCount]=wz;
                gItemType[gItemCount]=ITEM_STEIN; gItemAlive[gItemCount]=true; gItemCount++;
            }
        }

        Color avgColor(Color a, Color b, Color c) {
            return new Color(
                (a.getRed()+b.getRed()+c.getRed())/3,
                (a.getGreen()+b.getGreen()+c.getGreen())/3,
                (a.getBlue()+b.getBlue()+c.getBlue())/3);
        }

        // ──────────────────────────────────────────
        //  Game-Loop
        // ──────────────────────────────────────────
        void startLoop() {
            Timer t = new Timer(16, e -> {
                update();
                render();
                repaint();
            });
            t.start();
        }

        // ──────────────────────────────────────────
        //  Update
        // ──────────────────────────────────────────
        void update() {
            menuTick++;
            if (!gameState.equals("GAME")) return;
            if (inventoryOpen || craftingOpen) return;
            double rad = Math.toRadians(yaw);
            double dx = Math.sin(rad), dz = Math.cos(rad);
            if (keyW) { camX += dx * MOVE_SPEED; camZ += dz * MOVE_SPEED; }
            if (keyS) { camX -= dx * MOVE_SPEED; camZ -= dz * MOVE_SPEED; }
            if (keyA) { yaw -= TURN_SPEED; }
            if (keyD) { yaw += TURN_SPEED; }

            // ── Baum-Kollision: Spieler aus Stammradius herausschieben ──
            for (int i = 0; i < treeColCount; i++) {
                if (!treeAlive[i]) continue;  // gefällter Baum → kein Collider
                double ddx = camX - treeColX[i];
                double ddz = camZ - treeColZ[i];
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist < treeColR[i] && dist > 0.0001) {
                    double nx = ddx / dist;
                    double nz = ddz / dist;
                    camX = treeColX[i] + nx * treeColR[i];
                    camZ = treeColZ[i] + nz * treeColR[i];
                }
            }

            camY = sampleHeight(camX, camZ) + 1.65;
            waterTime += 0.03;

            // Item-Bob: schneller wenn wir laufen
            boolean moving = keyW || keyS;
            bobTime += moving ? 0.12 : 0.04;

            // ── Hunger: Sättigung verringern ──
            double drain = moving ? SAT_DRAIN_WALK : SAT_DRAIN_IDLE;
            saturation = Math.max(0, saturation - drain);
            if (saturation <= 20 && saturation > 0) hungerBlinkTimer++;
            if (hungerMsgTimer > 0) hungerMsgTimer--;

            // Swing beim Hotbar-Wechsel
            if (hotbarSelected != lastHotbarSelected) {
                swingTime = 0;
                lastHotbarSelected = hotbarSelected;
            }
            if (swingTime < Math.PI) swingTime += 0.18;
        }

        double sampleHeight(double wx, double wz) {
            double gx = (wx / SCALE) + GRID / 2.0;
            double gz = (wz / SCALE) + GRID / 2.0;
            gx = Math.max(0, Math.min(GRID - 0.001, gx));
            gz = Math.max(0, Math.min(GRID - 0.001, gz));
            int ix=(int)gx, iz=(int)gz;
            double fx=gx-ix, fz=gz-iz;
            double h00=heightMap[iz][ix];
            double h10=heightMap[iz][Math.min(GRID,ix+1)];
            double h01=heightMap[Math.min(GRID,iz+1)][ix];
            double h11=heightMap[Math.min(GRID,iz+1)][Math.min(GRID,ix+1)];
            return h00*(1-fx)*(1-fz)+h10*fx*(1-fz)+h01*(1-fx)*fz+h11*fx*fz;
        }

        // ──────────────────────────────────────────
        //  Render – Dispatcher
        // ──────────────────────────────────────────
        void render() {
            if (gameState.equals("MENU")) {
                drawStartMenu();
            } else if (gameState.equals("HELP")) {
                drawHelpScreen();
            } else {
                renderGame();
            }
        }

        // ══════════════════════════════════════════
        //  STARTMENÜ
        // ══════════════════════════════════════════
        void drawStartMenu() {
            // ── Hintergrund: animierter Himmel + Meer ──
            bg.setPaint(new GradientPaint(0,0,new Color(10,30,80),0,H,new Color(20,80,160)));
            bg.fillRect(0,0,W,H);

            // Wellen animiert
            for (int wave = 0; wave < 5; wave++) {
                double offset = menuTick * (0.8 + wave * 0.3);
                int waveY = H/2 + 60 + wave * 28;
                bg.setColor(new Color(30,100,200, 60 + wave * 15));
                for (int x = 0; x < W; x += 2) {
                    int y = waveY + (int)(Math.sin((x + offset) * 0.04) * 10);
                    bg.fillRect(x, y, 2, H - y);
                }
            }

            // Insel-Silhouette (stilisiert)
            bg.setColor(new Color(50,120,50,200));
            int[] islandX = {W/2-220, W/2-170, W/2-90, W/2-30, W/2+20, W/2+100, W/2+180, W/2+230};
            int[] islandY = {H/2+80,  H/2+40,  H/2+10, H/2-20, H/2-30, H/2,     H/2+50,  H/2+85};
            bg.fillPolygon(islandX, islandY, islandX.length);
            bg.setColor(new Color(238,214,175,200));
            int[] beachX = {W/2-240, W/2-180, W/2+190, W/2+250};
            int[] beachY = {H/2+95,  H/2+80,  H/2+80,  H/2+98};
            bg.fillPolygon(beachX, beachY, beachX.length);

            // Palmen-Silhouetten
            drawMenuPalm(W/2-130, H/2+5, -20);
            drawMenuPalm(W/2+80,  H/2-10,  15);

            // Sonne
            int sunX = 130, sunY = 90;
            bg.setColor(new Color(255,240,100,60));
            bg.fillOval(sunX-55,sunY-55,110,110);
            bg.setColor(new Color(255,230,80,120));
            bg.fillOval(sunX-38,sunY-38,76,76);
            bg.setColor(new Color(255,220,60,200));
            bg.fillOval(sunX-26,sunY-26,52,52);

            // Sterne (Hintergrund)
            Random starRng = new Random(7);
            bg.setColor(new Color(255,255,255,140));
            for (int i = 0; i < 40; i++) {
                int sx = starRng.nextInt(W);
                int sy = starRng.nextInt(H/2-30);
                int ss = 1 + starRng.nextInt(2);
                bg.fillOval(sx,sy,ss,ss);
            }

            // ── Titeltext ──
            // Schatten
            bg.setFont(new Font("SansSerif", Font.BOLD, 62));
            bg.setColor(new Color(0,0,0,120));
            drawCenteredString(bg, "🏝  ISLAND 3D", W/2+3, 113);
            // Haupttitel (Gradient-Effekt durch zwei überlagerte Texte)
            bg.setColor(new Color(255,230,60));
            drawCenteredString(bg, "🏝  ISLAND 3D", W/2, 110);
            bg.setFont(new Font("SansSerif", Font.PLAIN, 18));
            bg.setColor(new Color(200,240,255,200));
            drawCenteredString(bg, "Ein 3D First-Person Insel-Abenteuer", W/2, 143);

            // Trennlinie unter Titel
            bg.setColor(new Color(255,220,60,120));
            bg.fillRect(W/2-160, 155, 320, 2);

            // ── Buttons ──
            int btnW = 220, btnH = 50;
            int playX = W/2 - btnW/2, playY = H/2 - 20;
            int helpX = W/2 - btnW/2, helpY = H/2 + 50;

            btnPlay.setBounds(playX, playY, btnW, btnH);
            btnHelp.setBounds(helpX, helpY, btnW, btnH);

            drawMenuButton(bg, btnPlay, "▶  SPIELEN", new Color(60,180,80), new Color(40,130,55), true);
            drawMenuButton(bg, btnHelp, "?  ANLEITUNG", new Color(60,120,200), new Color(40,80,150), false);

            // ── Versionsinfo ──
            bg.setFont(new Font("Monospaced", Font.PLAIN, 10));
            bg.setColor(new Color(180,200,255,140));
            drawCenteredString(bg, "v1.0  •  BlueJ-kompatibel  •  Pure Java", W/2, H-15);

            // ── Tastatur-Hinweis ──
            bg.setFont(new Font("SansSerif", Font.PLAIN, 12));
            bg.setColor(new Color(200,230,255,160));
            drawCenteredString(bg, "[ ENTER ] zum Starten   [ H ] Hilfe", W/2, H-40);
        }

        /** Zeichnet eine stilisierte Palme im Menü. */
        void drawMenuPalm(int px, int py, int lean) {
            // Stamm
            bg.setColor(new Color(100,70,30,180));
            int[] stx = {px-5+lean, px+5+lean, px+8, px-8};
            int[] sty = {py-70,     py-70,      py,   py};
            bg.fillPolygon(stx,sty,4);
            // Blätter
            Color leafCol = new Color(40,110,40,200);
            bg.setColor(leafCol);
            int tx = px+lean, ty = py-70;
            int[][] leaves = {
                {tx,ty, tx-60,ty-20, tx-30,ty+10},
                {tx,ty, tx+60,ty-15, tx+25,ty+12},
                {tx,ty, tx-20,ty-50, tx+15,ty-35},
                {tx,ty, tx+10,ty-55, tx-15,ty-35},
                {tx,ty, tx-45,ty-45, tx-10,ty-15},
                {tx,ty, tx+45,ty-40, tx+12,ty-15},
            };
            for (int[] leaf : leaves)
                bg.fillPolygon(new int[]{leaf[0],leaf[2],leaf[4]},new int[]{leaf[1],leaf[3],leaf[5]},3);
        }

        /** Zeichnet einen abgerundeten Menü-Button. */
        void drawMenuButton(Graphics2D g, Rectangle r, String label, Color col, Color border, boolean highlight) {
            // Schatten
            g.setColor(new Color(0,0,0,80));
            g.fillRoundRect(r.x+4,r.y+4,r.width,r.height,16,16);
            // Hintergrund
            g.setPaint(new GradientPaint(r.x,r.y,col.brighter(),r.x,r.y+r.height,col.darker()));
            g.fillRoundRect(r.x,r.y,r.width,r.height,16,16);
            // Glanz
            g.setColor(new Color(255,255,255,50));
            g.fillRoundRect(r.x+4,r.y+4,r.width-8,r.height/2-4,10,10);
            // Rahmen
            g.setColor(highlight ? new Color(255,255,255,180) : border);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(r.x,r.y,r.width,r.height,16,16);
            g.setStroke(new BasicStroke(1f));
            // Text
            g.setFont(new Font("SansSerif",Font.BOLD,18));
            g.setColor(new Color(0,0,0,100));
            drawCenteredString(g, label, r.x+r.width/2+2, r.y+r.height/2+7);
            g.setColor(Color.WHITE);
            drawCenteredString(g, label, r.x+r.width/2, r.y+r.height/2+6);
        }

        // ══════════════════════════════════════════
        //  ANLEITUNGS-BILDSCHIRM
        // ══════════════════════════════════════════
        void drawHelpScreen() {
            // Hintergrund (wie Menü aber abgedunkelt)
            bg.setPaint(new GradientPaint(0,0,new Color(8,20,55),0,H,new Color(15,55,110)));
            bg.fillRect(0,0,W,H);

            // Seitenrand-Dekoration
            bg.setColor(new Color(255,220,60,30));
            bg.fillRoundRect(30,20,W-60,H-40,20,20);
            bg.setColor(new Color(255,220,60,100));
            bg.setStroke(new BasicStroke(2f));
            bg.drawRoundRect(30,20,W-60,H-40,20,20);
            bg.setStroke(new BasicStroke(1f));

            // Innerer Rahmen
            bg.setColor(new Color(255,220,60,40));
            bg.setStroke(new BasicStroke(1f));
            bg.drawRoundRect(38,28,W-76,H-56,14,14);
            bg.setStroke(new BasicStroke(1f));

            // Titel
            bg.setFont(new Font("SansSerif",Font.BOLD,26));
            bg.setColor(new Color(255,220,60));
            drawCenteredString(bg,"📋  SPIELANLEITUNG",W/2,62);
            bg.setColor(new Color(255,220,60,80));
            bg.fillRect(80,72,W-160,2);

            // ── Zweispaltiges Layout ──
            int col1X = 80,  col2X = W/2+20;
            int startY = 95, lineH = 22;

            // Spalte 1 – Bewegung & Aktionen
            drawHelpSection(bg, "🎮  STEUERUNG", col1X, startY);
            int y = startY + 30;
            y = drawHelpRow(bg, "W / ↑",       "Vorwärts laufen",       col1X, y); y += 2;
            y = drawHelpRow(bg, "S / ↓",       "Rückwärts laufen",      col1X, y); y += 2;
            y = drawHelpRow(bg, "A / ←",       "Nach links drehen",     col1X, y); y += 2;
            y = drawHelpRow(bg, "D / →",       "Nach rechts drehen",    col1X, y); y += 2;
            y = drawHelpRow(bg, "Maus ziehen", "Umschauen (H & V)",     col1X, y); y += 2;
            y = drawHelpRow(bg, "Mausrad",     "Hotbar-Slot wechseln",  col1X, y); y += 12;

            drawHelpSection(bg, "⚡  AKTIONEN", col1X, y);
            y += 30;
            y = drawHelpRow(bg, "C",    "Gegenstände aufsammeln", col1X, y); y += 2;
            y = drawHelpRow(bg, "E",    "Inventar öffnen/schließen", col1X, y); y += 2;
            y = drawHelpRow(bg, "Q",    "Crafting-Buch öffnen",  col1X, y); y += 2;
            y = drawHelpRow(bg, "Klick","Baum fällen (mit Axt)", col1X, y); y += 2;
            y = drawHelpRow(bg, "R-Klick","Item benutzen / trinken", col1X, y); y += 2;
            y = drawHelpRow(bg, "1–9",  "Hotbar-Slot auswählen", col1X, y); y += 2;
            y = drawHelpRow(bg, "F11",  "Vollbild umschalten",   col1X, y);

            // Spalte 2 – Welt & Crafting
            drawHelpSection(bg, "🌍  DIE WELT", col2X, startY);
            y = startY + 30;
            y = drawHelpPara(bg, "Erkunde eine kleine tropische Insel in", col2X, y);
            y = drawHelpPara(bg, "der First-Person-Perspektive. Sammle", col2X, y);
            y = drawHelpPara(bg, "Ressourcen und stelle nützliche Items her.", col2X, y);
            y += 12;

            drawHelpSection(bg, "🎒  RESSOURCEN SAMMELN", col2X, y);
            y += 30;
            y = drawHelpRow(bg, "●  Beeren",  "An Sträuchern im Gras",  col2X, y); y += 2;
            y = drawHelpRow(bg, "/  Stöcke",  "Auf dem Boden liegend",  col2X, y); y += 2;
            y = drawHelpRow(bg, "▪  Steine",  "Überall auf der Insel",  col2X, y); y += 12;

            drawHelpSection(bg, "⚒  CRAFTING", col2X, y);
            y += 30;
            y = drawHelpRow(bg, "Axt",        "Stock (1) + Stein (2)",  col2X, y); y += 2;
            y = drawHelpRow(bg, "Beerensaft", "Beere (3)",              col2X, y); y += 12;

            drawHelpSection(bg, "🧭  KOMPASS", col2X, y);
            y += 30;
            drawHelpPara(bg, "Oben rechts zeigt der Kompass deine", col2X, y); y += lineH;
            drawHelpPara(bg, "Blickrichtung (rot = Norden).", col2X, y);

            // Trennlinie
            bg.setColor(new Color(255,220,60,60));
            bg.fillRect(80,H-75,W-160,1);

            // Zurück-Button
            int bW = 180, bH = 40;
            int bX = W/2-bW/2, bY = H-65;
            btnBack.setBounds(bX,bY,bW,bH);
            drawMenuButton(bg, btnBack, "← ZURÜCK", new Color(160,60,60), new Color(120,40,40), false);

            // Tastatur-Tipp
            bg.setFont(new Font("SansSerif",Font.PLAIN,11));
            bg.setColor(new Color(180,200,255,130));
            drawCenteredString(bg,"[ ESC ] oder [ BACKSPACE ] zum Zurückkehren",W/2,H-18);
        }

        /** Zeichnet einen Abschnittstitel in der Anleitung. */
        void drawHelpSection(Graphics2D g, String title, int x, int y) {
            g.setFont(new Font("SansSerif",Font.BOLD,13));
            g.setColor(new Color(255,220,60));
            g.drawString(title, x, y);
            g.setColor(new Color(255,220,60,60));
            FontMetrics fm = g.getFontMetrics();
            g.fillRect(x, y+3, fm.stringWidth(title), 1);
        }

        /** Zeichnet eine Taste + Beschreibung Zeile. Gibt neue Y-Position zurück. */
        int drawHelpRow(Graphics2D g, String key, String desc, int x, int y) {
            // Taste – Pillen-Box
            g.setFont(new Font("Monospaced",Font.BOLD,11));
            FontMetrics fm = g.getFontMetrics();
            int kW = Math.max(fm.stringWidth(key)+14, 36);
            g.setColor(new Color(60,80,140,200));
            g.fillRoundRect(x,y-13,kW,18,6,6);
            g.setColor(new Color(100,140,220,180));
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(x,y-13,kW,18,6,6);
            g.setColor(new Color(200,220,255));
            g.drawString(key, x+(kW-fm.stringWidth(key))/2, y);
            // Beschreibung
            g.setFont(new Font("SansSerif",Font.PLAIN,12));
            g.setColor(new Color(200,230,255,220));
            g.drawString(desc, x+kW+10, y);
            return y + 21;
        }

        /** Zeichnet einen Fließtext-Satz. Gibt neue Y-Position zurück. */
        int drawHelpPara(Graphics2D g, String text, int x, int y) {
            g.setFont(new Font("SansSerif",Font.PLAIN,12));
            g.setColor(new Color(190,220,255,200));
            g.drawString(text, x, y);
            return y + 19;
        }

        /** Hilfsfunktion: Text horizontal zentriert zeichnen. */
        void drawCenteredString(Graphics2D g, String s, int cx, int y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, cx - fm.stringWidth(s)/2, y);
        }

        // ──────────────────────────────────────────
        //  3D-Spielwelt rendern (unveränderter Original-Code)
        // ──────────────────────────────────────────
        void renderGame() {
            drawSky();

            double yawRad   = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            List<Triangle> all = new ArrayList<>(triangles);

            for (Triangle tri : waterTriangles) {
                double wave = Math.sin(tri.cx * 0.8 + waterTime) * 0.04
                            + Math.cos(tri.cz * 0.6 + waterTime * 0.7) * 0.03;
                // Basis-Farbe aus gespeichertem Dreieck, leicht durch Welle moduliert
                int baseR = tri.color.getRed();
                int baseG = tri.color.getGreen();
                int baseB = tri.color.getBlue();
                int modG  = Math.min(180, Math.max(0, baseG + (int)(wave * 120)));
                int modB  = Math.min(255, Math.max(0, baseB + (int)(wave * 160)));
                all.add(new Triangle(tri.x0, tri.y0+wave, tri.z0,
                                     tri.x1, tri.y1+wave, tri.z1,
                                     tri.x2, tri.y2+wave, tri.z2,
                                     new Color(baseR, modG, modB, tri.color.getAlpha())));
            }

            all.addAll(shrubTriangles);
            all.addAll(treeTriangles);

            Color berryCol  = new Color(200,20,20);
            Color berryDark = new Color(140,10,10);
            double br = 0.09;
            for (int i = 0; i < berryCount; i++) {
                if (!berryAlive[i]) continue;
                double bx=berryX[i], by=berryY[i], bz=berryZ[i];
                for (int s = 0; s < 4; s++) {
                    double a0=2*Math.PI*s/4, a1=2*Math.PI*(s+1)/4;
                    all.add(new Triangle(
                        bx+Math.cos(a0)*br, by, bz+Math.sin(a0)*br,
                        bx+Math.cos(a1)*br, by, bz+Math.sin(a1)*br,
                        bx, by+br, bz,
                        (s%2==0)?berryCol:berryDark));
                }
            }

            for (int i = 0; i < gItemCount; i++) {
                if (!gItemAlive[i]) continue;
                double ix=gItemX[i], iy=gItemY[i], iz=gItemZ[i];
                if (gItemType[i]==ITEM_STOCK) {
                    Color sc=new Color(120,75,30), sc2=new Color(90,55,20);
                    double len=0.35, hw=0.04;
                    double rot=(ix*3.7+iz*2.3)%(Math.PI);
                    double cx=Math.cos(rot)*len, cz=Math.sin(rot)*len;
                    double px=Math.cos(rot+Math.PI/2)*hw, pz=Math.sin(rot+Math.PI/2)*hw;
                    double gy=iy+0.02;
                    all.add(new Triangle(ix-cx+px,gy,iz-cz+pz, ix+cx+px,gy,iz+cz+pz, ix+cx-px,gy,iz+cz-pz, sc));
                    all.add(new Triangle(ix-cx+px,gy,iz-cz+pz, ix+cx-px,gy,iz+cz-pz, ix-cx-px,gy,iz-cz-pz, sc));
                    all.add(new Triangle(ix-cx+px,gy,iz-cz+pz, ix+cx+px,gy,iz+cz+pz, ix+cx+px,gy-0.04,iz+cz+pz, sc2));
                    all.add(new Triangle(ix-cx-px,gy,iz-cz-pz, ix+cx-px,gy,iz+cz-pz, ix+cx-px,gy-0.04,iz+cz-pz, sc2));
                } else {
                    Color rc=new Color(160,160,155), rc2=new Color(120,120,115);
                    double r1=0.14, r2=0.10;
                    double gy=iy+0.01;
                    int segs=5;
                    for (int s=0; s<segs; s++) {
                        double a0=2*Math.PI*s/segs, a1=2*Math.PI*(s+1)/segs;
                        double ra=(s%2==0)?r1:r2, rb=(s%2==0)?r2:r1;
                        all.add(new Triangle(
                            ix+Math.cos(a0)*ra,gy+0.03,iz+Math.sin(a0)*ra,
                            ix+Math.cos(a1)*rb,gy+0.03,iz+Math.sin(a1)*rb,
                            ix,gy+0.05,iz, (s%2==0)?rc:rc2));
                        all.add(new Triangle(
                            ix+Math.cos(a0)*ra,gy+0.03,iz+Math.sin(a0)*ra,
                            ix+Math.cos(a1)*rb,gy+0.03,iz+Math.sin(a1)*rb,
                            ix+Math.cos(a0)*ra,gy,iz+Math.sin(a0)*ra, rc2));
                    }
                }
            }

            all.sort((a, b) -> Double.compare(distSq(b), distSq(a)));
            for (Triangle tri : all) projectAndDraw(tri, yawRad, pitchRad);

            drawHUD();
            if (!inventoryOpen && !craftingOpen) drawHeldItem();
            if (inventoryOpen) drawInventory();
            if (craftingOpen)  drawCraftingBook();
        }

        double distSq(Triangle t) {
            double cx=t.cx-camX, cy=t.cy-camY, cz=t.cz-camZ;
            return cx*cx+cy*cy+cz*cz;
        }

        void drawSky() {
            bg.setPaint(new GradientPaint(0,0,new Color(135,206,250),0,H/2,new Color(30,80,160)));
            bg.fillRect(0,0,W,H/2+40);
            bg.setPaint(new GradientPaint(0,H/2-30,new Color(200,230,255,180),0,H/2+30,new Color(200,230,255,0)));
            bg.fillRect(0,H/2-30,W,60);
            bg.setColor(new Color(10, 40, 110));
            bg.fillRect(0,H/2,W,H);
            bg.setColor(new Color(255,240,100,180));
            bg.fillOval(W-140,30,70,70);
            bg.setColor(new Color(255,220,80,80));
            bg.fillOval(W-150,20,90,90);
        }

        static final double FOV=600, NEAR=0.5, FAR=80.0, CLIP_TOP=-5;

        void projectAndDraw(Triangle tri, double yawRad, double pitchRad) {
            double cosY=Math.cos(-yawRad), sinY=Math.sin(-yawRad);
            double cosP=Math.cos(Math.toRadians(-pitch)), sinP=Math.sin(Math.toRadians(-pitch));
            int[] sx=new int[3], sy=new int[3];
            boolean anyVisible=false;
            double[] worldX={tri.x0,tri.x1,tri.x2};
            double[] worldY={tri.y0,tri.y1,tri.y2};
            double[] worldZ={tri.z0,tri.z1,tri.z2};
            for (int i=0; i<3; i++) {
                double rx=worldX[i]-camX, ry=worldY[i]-camY, rz=worldZ[i]-camZ;
                double tx=rx*cosY+rz*sinY, tz=-rx*sinY+rz*cosY, ty=ry;
                double ty2=ty*cosP-tz*sinP, tz2=ty*sinP+tz*cosP;
                if (tz2<NEAR) return;
                anyVisible=true;
                sx[i]=(int)(W/2+tx/tz2*FOV);
                sy[i]=(int)(H/2-ty2/tz2*FOV);
            }
            if (!anyVisible) return;
            Color lit=shade(tri.color,computeLight(tri));
            int[] px={sx[0],sx[1],sx[2]}, py={sy[0],sy[1],sy[2]};
            boolean onScreen=false;
            for (int i=0; i<3; i++) {
                if (px[i]>CLIP_TOP&&px[i]<W+200&&py[i]>CLIP_TOP&&py[i]<H+200) { onScreen=true; break; }
            }
            if (!onScreen) return;
            bg.setColor(lit);
            bg.fillPolygon(px,py,3);
            if (tri.color.getAlpha()==255) { bg.setColor(lit.darker()); bg.drawPolygon(px,py,3); }
        }

        double computeLight(Triangle tri) {
            double ax=tri.x1-tri.x0, ay=tri.y1-tri.y0, az=tri.z1-tri.z0;
            double bx=tri.x2-tri.x0, by=tri.y2-tri.y0, bz=tri.z2-tri.z0;
            double nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
            double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if (len==0) return 1;
            nx/=len; ny/=len; nz/=len;
            return 0.3+0.7*Math.max(0,nx*0.6+ny*0.8+nz*(-0.3));
        }

        Color shade(Color c, double light) {
            return new Color(
                Math.min(255,(int)(c.getRed()*light)),
                Math.min(255,(int)(c.getGreen()*light)),
                Math.min(255,(int)(c.getBlue()*light)),
                c.getAlpha());
        }

        // ──────────────────────────────────────────
        //  HAND-ITEM 3D-Ansicht
        // ──────────────────────────────────────────
        void drawHeldItem() {
            int selItem = hotbarItems[hotbarSelected];
            if (selItem < 0) return; // leerer Slot → nichts zeichnen

            // ── Animationsoffsets ──
            double bob   = Math.sin(bobTime)       * 18.0;  // auf/ab
            double sway  = Math.sin(bobTime * 0.5) * 10.5;  // links/rechts
            // Swing-Einflug beim Item-Wechsel: kurzes Einschwingen von unten
            double swing = Math.sin(swingTime) * 120.0 * Math.max(0, 1.0 - swingTime / Math.PI);

            // Basisposition: rechts unten, korrekt berechnet für 3x-Skala
            // proj: px = bx + x*120 - z*84,  py = by - y*120 - z*48
            // Arm-Unterseite (y=-0.12) liegt bei by+14 → knapp unterhalb des Bildschirms
            // Block-Oberseite (y=0.82)  liegt bei by-113 → sichtbar
            int baseX = W - 30;
            int baseY = (int)(H - 30 + bob + swing);

            // ── Arm/Hand zeichnen ──
            drawHand(baseX, baseY, sway);

            // ── 3D-Modell des Items ──
            Color itemCol  = MASTER_COLORS[selItem];
            Color itemDark = itemCol.darker();
            Color itemLight = itemCol.brighter();

            switch (selItem) {
                case 0: // Holz → Holzblock
                    drawHeldBlock(baseX, baseY, sway,
                        new Color(139,90,43), new Color(100,65,30), new Color(170,115,60));
                    break;
                case 1: // Stein (Inventar) → Steinblock
                    drawHeldBlock(baseX, baseY, sway,
                        new Color(140,140,140), new Color(100,100,100), new Color(175,175,175));
                    break;
                case 2: // Sand → flacher Sandblock
                    drawHeldBlock(baseX, baseY, sway,
                        new Color(238,214,175), new Color(200,180,140), new Color(255,235,200));
                    break;
                case 3: // Gras → Grasblock
                    drawHeldGrassBlock(baseX, baseY, sway);
                    break;
                case 4: // Wasser → Wasserblock (transparent)
                    drawHeldBlock(baseX, baseY, sway,
                        new Color(30,100,200,200), new Color(20,70,150,200), new Color(60,140,230,200));
                    break;
                case ITEM_BEERE: // Beere → rote Kugel
                    drawHeldSphere(baseX, baseY, sway,
                        new Color(200,20,20), new Color(140,10,10), new Color(230,80,80));
                    break;
                case ITEM_STOCK: // Stock → langer Stab
                    drawHeldStick(baseX, baseY, sway);
                    break;
                case ITEM_STEIN: // Stein (Boden) → unregelmäßiger Stein
                    drawHeldRock(baseX, baseY, sway);
                    break;
                case ITEM_AXT: // Axt → Axt-Form
                    drawHeldAxe(baseX, baseY, sway);
                    break;
                case ITEM_SAFT: // Beerensaft → Flasche
                    drawHeldBottle(baseX, baseY, sway);
                    break;
                default: // Fallback: einfacher Block in Itemfarbe
                    drawHeldBlock(baseX, baseY, sway, itemCol, itemDark, itemLight);
                    break;
            }
        }

        /** Projiziert einen 3D-Punkt in 2D für die Hand-Ansicht (orthogonale Schrägsicht). */
        int[] proj(double x, double y, double z, int bx, int by, double sway) {
            // Schrägsicht: X leicht nach rechts, Z nach links-unten, Y nach oben
            double px = bx + x * 120  - z * 84 + sway;
            double py = by - y * 120  - z * 48;
            return new int[]{(int)px, (int)py};
        }

        /** Zeichnet eine isometrische Box (Quader) für die Hand-Ansicht. */
        void drawHeldBox(int bx, int by, double sway,
                         double x0, double y0, double z0,
                         double x1, double y1, double z1,
                         Color top, Color front, Color side) {
            // ── Seiten-Fläche (rechts, sichtbar) ──
            int[] p000=proj(x0,y0,z0,bx,by,sway), p100=proj(x1,y0,z0,bx,by,sway);
            int[] p110=proj(x1,y1,z0,bx,by,sway), p010=proj(x0,y1,z0,bx,by,sway);
            int[] p001=proj(x0,y0,z1,bx,by,sway), p101=proj(x1,y0,z1,bx,by,sway);
            int[] p111=proj(x1,y1,z1,bx,by,sway), p011=proj(x0,y1,z1,bx,by,sway);

            // Front (z=z1)
            bg.setColor(front);
            bg.fillPolygon(new int[]{p001[0],p101[0],p111[0],p011[0]},
                           new int[]{p001[1],p101[1],p111[1],p011[1]}, 4);
            // Seite (x=x1)
            bg.setColor(side);
            bg.fillPolygon(new int[]{p100[0],p101[0],p111[0],p110[0]},
                           new int[]{p100[1],p101[1],p111[1],p110[1]}, 4);
            // Deckel (y=y1)
            bg.setColor(top);
            bg.fillPolygon(new int[]{p010[0],p110[0],p111[0],p011[0]},
                           new int[]{p010[1],p110[1],p111[1],p011[1]}, 4);

            // Kanten
            bg.setColor(new Color(0,0,0,80));
            bg.setStroke(new BasicStroke(1f));
            bg.drawPolygon(new int[]{p001[0],p101[0],p111[0],p011[0]},
                           new int[]{p001[1],p101[1],p111[1],p011[1]}, 4);
            bg.drawPolygon(new int[]{p100[0],p101[0],p111[0],p110[0]},
                           new int[]{p100[1],p101[1],p111[1],p110[1]}, 4);
            bg.drawPolygon(new int[]{p010[0],p110[0],p111[0],p011[0]},
                           new int[]{p010[1],p110[1],p111[1],p011[1]}, 4);
        }

        /** Zeichnet den Arm/Hand als Körperteil. */
        void drawHand(int bx, int by, double sway) {
            // Unterarm (ein schmaler Quader)
            Color skinLight = new Color(220, 175, 130);
            Color skinMid   = new Color(190, 145, 100);
            Color skinDark  = new Color(160, 115,  75);
            drawHeldBox(bx, by, sway,
                -0.18, -0.12, -0.10,
                 0.18,  0.12,  0.55,
                skinMid, skinLight, skinDark);
        }

        void drawHeldBlock(int bx, int by, double sway, Color top, Color front, Color side) {
            drawHeldBox(bx, by, sway,
                -0.5, 0.12, -0.4,
                 0.3, 0.82,  0.3,
                top, front, side);
        }

        void drawHeldGrassBlock(int bx, int by, double sway) {
            // Erd-Teil
            drawHeldBox(bx, by, sway,
                -0.5, 0.12, -0.4,
                 0.3, 0.62,  0.3,
                new Color(86,155,56), new Color(139,90,43), new Color(110,70,35));
            // Gras-Schicht oben
            drawHeldBox(bx, by, sway,
                -0.5, 0.62, -0.4,
                 0.3, 0.82,  0.3,
                new Color(86,155,56), new Color(70,140,45), new Color(60,130,40));
        }

        void drawHeldSphere(int bx, int by, double sway, Color mid, Color dark, Color light) {
            // Kugel aus gestapelten Ellipsen
            int cx = bx + (int)(-0.1*120 - 0.05*(-84) + sway);
            int cy = by - (int)(0.5*120 + 0.05*(-48));
            int rx = 66, ry = 60;
            bg.setColor(dark);        bg.fillOval(cx-rx,   cy-ry,   rx*2,   ry*2);
            bg.setColor(mid);         bg.fillOval(cx-rx+9, cy-ry+6, rx*2-15, ry*2-12);
            bg.setColor(light);       bg.fillOval(cx-rx/2, cy-ry/2, rx/2, ry/2);  // Glanzpunkt
            bg.setColor(new Color(0,0,0,60));
            bg.drawOval(cx-rx, cy-ry, rx*2, ry*2);
        }

        void drawHeldStick(int bx, int by, double sway) {
            // Langer schräger Stab
            Color sc  = new Color(120, 75, 30);
            Color sc2 = new Color(90, 55, 20);
            Color sc3 = new Color(150, 95, 50);
            // Stab: dünner langer Quader, diagonal gehalten
            drawHeldBox(bx, by, sway,
                -0.07, 0.12, -0.05,
                 0.07, 1.45,  0.05,
                sc3, sc, sc2);
            // Rinde-Textur (zwei Querstreifen)
            bg.setColor(new Color(80,50,15,120));
            int[] sp1 = proj(-0.07, 0.5, 0.05, bx, by, sway);
            int[] sp2 = proj( 0.07, 0.5, 0.05, bx, by, sway);
            int[] sp3 = proj( 0.07, 0.55, 0.05, bx, by, sway);
            int[] sp4 = proj(-0.07, 0.55, 0.05, bx, by, sway);
            bg.fillPolygon(new int[]{sp1[0],sp2[0],sp3[0],sp4[0]},
                           new int[]{sp1[1],sp2[1],sp3[1],sp4[1]}, 4);
        }

        void drawHeldRock(int bx, int by, double sway) {
            Color rc  = new Color(160,160,155);
            Color rc2 = new Color(120,120,115);
            Color rc3 = new Color(190,190,185);
            // Unregelmäßiger Stein: drei überlagerte Box-Schichten
            drawHeldBox(bx, by, sway, -0.45, 0.12, -0.38, 0.25, 0.65, 0.28, rc3, rc, rc2);
            drawHeldBox(bx, by, sway, -0.38, 0.55, -0.30, 0.20, 0.82, 0.20, rc, rc2, rc3);
        }

        void drawHeldAxe(int bx, int by, double sway) {
            // Stiel
            Color wood  = new Color(120,75,30);
            Color wood2 = new Color(90,55,20);
            Color wood3 = new Color(150,95,50);
            drawHeldBox(bx, by, sway, -0.06, 0.12, -0.05, 0.06, 1.30, 0.05, wood3, wood, wood2);
            // Axtkopf (flacher breiter Quader oben)
            Color metal  = new Color(180,180,180);
            Color metal2 = new Color(130,130,130);
            Color metal3 = new Color(210,210,210);
            drawHeldBox(bx, by, sway, -0.35, 0.95, -0.08, 0.15, 1.35, 0.08, metal3, metal, metal2);
            // Schneide (dünner Streifen)
            drawHeldBox(bx, by, sway, -0.38, 1.05, -0.04, -0.28, 1.28, 0.04, metal3, metal3, metal);
        }

        void drawHeldBottle(int bx, int by, double sway) {
            // Flaschen-Körper (breiter Quader)
            Color glass  = new Color(180,30,80,210);
            Color glass2 = new Color(130,20,55,210);
            Color glass3 = new Color(210,80,120,210);
            drawHeldBox(bx, by, sway, -0.22, 0.12, -0.18, 0.18, 0.75, 0.18, glass3, glass, glass2);
            // Flaschenhals (schmaler)
            Color neck  = new Color(160,25,65,220);
            Color neck2 = new Color(120,18,48,220);
            drawHeldBox(bx, by, sway, -0.10, 0.75, -0.10, 0.06, 1.05, 0.10, neck, neck, neck2);
            // Korken
            Color cork = new Color(200,160,100);
            drawHeldBox(bx, by, sway, -0.09, 1.04, -0.09, 0.05, 1.13, 0.09, cork, cork.darker(), cork.brighter());
            // Glanz
            bg.setColor(new Color(255,255,255,60));
            int[] gl1 = proj(-0.18, 0.35, 0.18, bx, by, sway);
            int[] gl2 = proj(-0.10, 0.35, 0.18, bx, by, sway);
            int[] gl3 = proj(-0.10, 0.60, 0.18, bx, by, sway);
            int[] gl4 = proj(-0.18, 0.60, 0.18, bx, by, sway);
            bg.fillPolygon(new int[]{gl1[0],gl2[0],gl3[0],gl4[0]},
                           new int[]{gl1[1],gl2[1],gl3[1],gl4[1]}, 4);
        }

        void drawHUD() {
            int cx=W/2, cy=H/2;
            bg.setColor(new Color(255,255,255,180));
            bg.drawLine(cx-12,cy,cx+12,cy); bg.drawLine(cx,cy-12,cx,cy+12);
            bg.setColor(new Color(0,0,0,100));
            bg.drawLine(cx-11,cy,cx+11,cy); bg.drawLine(cx,cy-11,cx,cy+11);
            drawCompass();
            drawHungerBar();
            drawHotbar();
            if (collectTimer>0) {
                collectTimer--;
                float alpha=Math.min(1f,collectTimer/30f);
                bg.setFont(new Font("SansSerif",Font.BOLD,16));
                FontMetrics fm=bg.getFontMetrics();
                int mw=fm.stringWidth(collectMsg), mx=(W-mw)/2, my=H/2+60;
                bg.setColor(new Color(0,0,0,(int)(160*alpha)));
                bg.fillRoundRect(mx-10,my-18,mw+20,26,10,10);
                bg.setColor(new Color(255,80,80,(int)(255*alpha)));
                bg.drawString(collectMsg,mx,my);
            }
            if (chopTimer>0) {
                chopTimer--;
                float alpha=Math.min(1f,chopTimer/30f);
                bg.setFont(new Font("SansSerif",Font.BOLD,16));
                FontMetrics fm=bg.getFontMetrics();
                int mw=fm.stringWidth(chopMsg), mx=(W-mw)/2, my=H/2+90;
                bg.setColor(new Color(0,0,0,(int)(160*alpha)));
                bg.fillRoundRect(mx-10,my-18,mw+20,26,10,10);
                bg.setColor(new Color(100,200,60,(int)(255*alpha)));
                bg.drawString(chopMsg,mx,my);
            }
            if (hungerMsgTimer>0) {
                hungerMsgTimer--;
                float alpha=Math.min(1f,hungerMsgTimer/30f);
                bg.setFont(new Font("SansSerif",Font.BOLD,16));
                FontMetrics fm=bg.getFontMetrics();
                int mw=fm.stringWidth(hungerMsg), mx=(W-mw)/2, my=H/2+120;
                bg.setColor(new Color(0,0,0,(int)(160*alpha)));
                bg.fillRoundRect(mx-10,my-18,mw+20,26,10,10);
                bg.setColor(new Color(255,180,40,(int)(255*alpha)));
                bg.drawString(hungerMsg,mx,my);
            }
        }

        // ──────────────────────────────────────────
        //  HUNGERLEISTE
        // ──────────────────────────────────────────
        void drawHungerBar() {
            final int SLOT_SIZE = 52, SLOT_GAP = 4;
            final int TOTAL_W   = HOTBAR_SLOTS * SLOT_SIZE + (HOTBAR_SLOTS - 1) * SLOT_GAP + 16;
            final int barX      = (W - TOTAL_W) / 2;
            final int hotbarY   = H - SLOT_SIZE - 30;
            final int barY      = hotbarY - 28;
            final int barW      = TOTAL_W;
            final int barH      = 14;
            final int CORNER    = 7;

            double ratio = saturation / SAT_MAX;

            // Blink-Effekt bei niedrigem Hunger (< 20%)
            boolean showBar = true;
            if (saturation <= 20 && saturation > 0) {
                showBar = (hungerBlinkTimer / 15) % 2 == 0;
            }

            // Hintergrund-Leiste
            bg.setColor(new Color(0,0,0,160));
            bg.fillRoundRect(barX-4, barY-4, barW+8, barH+8, CORNER+2, CORNER+2);
            bg.setColor(new Color(60,30,10,200));
            bg.fillRoundRect(barX, barY, barW, barH, CORNER, CORNER);

            if (showBar && ratio > 0) {
                // Farbe je nach Füllstand: grün → gelb → orange → rot
                Color fillColor;
                if      (ratio > 0.6) fillColor = new Color(60,  200,  60);
                else if (ratio > 0.35) fillColor = new Color(210, 180,  20);
                else if (ratio > 0.15) fillColor = new Color(220, 100,  20);
                else                   fillColor = new Color(200,  30,  30);

                int fillW = (int)(barW * ratio);
                bg.setColor(fillColor);
                bg.fillRoundRect(barX, barY, fillW, barH, CORNER, CORNER);

                // Glanz-Streifen
                bg.setColor(new Color(255,255,255,50));
                bg.fillRoundRect(barX+2, barY+2, fillW-4, barH/2-2, CORNER-2, CORNER-2);
            }

            // Rahmen
            bg.setColor(new Color(255,255,255,60));
            bg.setStroke(new BasicStroke(1f));
            bg.drawRoundRect(barX, barY, barW, barH, CORNER, CORNER);

            // Icon (Gabel/Herz) links neben der Leiste
            bg.setFont(new Font("SansSerif", Font.BOLD, 13));
            bg.setColor(new Color(255,200,80,200));
            bg.drawString("🍴", barX - 22, barY + barH - 1);

            // Prozentzahl mittig
            bg.setFont(new Font("Monospaced", Font.BOLD, 10));
            String pct = (int)saturation + "%";
            FontMetrics fm = bg.getFontMetrics();
            bg.setColor(new Color(0,0,0,140));
            bg.drawString(pct, barX + (barW - fm.stringWidth(pct))/2 + 1, barY + barH - 2);
            bg.setColor(Color.WHITE);
            bg.drawString(pct, barX + (barW - fm.stringWidth(pct))/2, barY + barH - 3);

            // Warnung bei leerem Hunger
            if (saturation <= 0) {
                bg.setFont(new Font("SansSerif", Font.BOLD, 14));
                fm = bg.getFontMetrics();
                String warn = "⚠ Hunger! Trink Beerensaft (Rechtsklick)";
                int wx2 = (W - fm.stringWidth(warn)) / 2;
                int wy2 = barY - 12;
                bg.setColor(new Color(0,0,0,160));
                bg.fillRoundRect(wx2-8, wy2-16, fm.stringWidth(warn)+16, 22, 8, 8);
                boolean blink = (hungerBlinkTimer / 10) % 2 == 0;
                bg.setColor(blink ? new Color(255,60,60) : new Color(255,160,40));
                bg.drawString(warn, wx2, wy2);
            }
        }

        void drawCompass() {
            int cx=W-55, cy=55, r=35;
            bg.setColor(new Color(0,0,0,140)); bg.fillOval(cx-r,cy-r,r*2,r*2);
            bg.setColor(new Color(255,255,255,80)); bg.drawOval(cx-r,cy-r,r*2,r*2);
            double a=Math.toRadians(yaw);
            int nx=(int)(cx+Math.sin(a)*(r-8)), nz=(int)(cy-Math.cos(a)*(r-8));
            bg.setColor(new Color(255,80,80)); bg.fillOval(nx-5,nz-5,10,10);
            bg.setFont(new Font("SansSerif",Font.BOLD,11));
            bg.setColor(Color.WHITE);
            bg.drawString("N",cx-4,cy-r+14); bg.drawString("S",cx-4,cy+r-4);
            bg.drawString("W",cx-r+4,cy+4);  bg.drawString("O",cx+r-12,cy+4);
        }

        void drawHotbar() {
            final int SLOT_SIZE=52, SLOT_GAP=4, CORNER=8;
            final int TOTAL_W=HOTBAR_SLOTS*SLOT_SIZE+(HOTBAR_SLOTS-1)*SLOT_GAP+16;
            final int startX=(W-TOTAL_W)/2, startY=H-SLOT_SIZE-30;
            bg.setColor(new Color(0,0,0,160));
            bg.fillRoundRect(startX-4,startY-4,TOTAL_W+8,SLOT_SIZE+12,CORNER+4,CORNER+4);
            bg.setColor(new Color(255,255,255,40));
            bg.drawRoundRect(startX-4,startY-4,TOTAL_W+8,SLOT_SIZE+12,CORNER+4,CORNER+4);
            for (int i=0; i<HOTBAR_SLOTS; i++) {
                int x=startX+8+i*(SLOT_SIZE+SLOT_GAP), y=startY+4;
                boolean selected=(i==hotbarSelected);
                int item=hotbarItems[i];
                if (selected) {
                    bg.setColor(new Color(255,255,255,60));
                    bg.fillRoundRect(x-3,y-3,SLOT_SIZE+6,SLOT_SIZE+6,CORNER,CORNER);
                    bg.setColor(new Color(255,220,50,220));
                    bg.setStroke(new BasicStroke(2.5f));
                    bg.drawRoundRect(x-3,y-3,SLOT_SIZE+6,SLOT_SIZE+6,CORNER,CORNER);
                    bg.setStroke(new BasicStroke(1.0f));
                } else {
                    bg.setColor(new Color(50,50,50,140));
                    bg.fillRoundRect(x,y,SLOT_SIZE,SLOT_SIZE,CORNER,CORNER);
                    bg.setColor(new Color(255,255,255,50));
                    bg.drawRoundRect(x,y,SLOT_SIZE,SLOT_SIZE,CORNER,CORNER);
                }
                int iconX=x+8, iconY=y+8, iconW=SLOT_SIZE-16, iconH=SLOT_SIZE-16;
                if (item>=0) {
                    bg.setColor(MASTER_COLORS[item]); bg.fillRoundRect(iconX,iconY,iconW,iconH,6,6);
                    bg.setColor(new Color(255,255,255,60)); bg.fillRoundRect(iconX+2,iconY+2,iconW-4,iconH/2-2,4,4);
                    String icon=MASTER_ICONS[item]; int fontSize=icon.length()>2?9:13;
                    bg.setFont(new Font("SansSerif",Font.BOLD,fontSize));
                    FontMetrics fm=bg.getFontMetrics();
                    int tx=iconX+(iconW-fm.stringWidth(icon))/2;
                    int ty=iconY+(iconH+fm.getAscent()-fm.getDescent())/2;
                    bg.setColor(new Color(0,0,0,160)); bg.drawString(icon,tx+1,ty+1);
                    bg.setColor(Color.WHITE); bg.drawString(icon,tx,ty);
                } else {
                    bg.setColor(new Color(60,60,60)); bg.fillRoundRect(iconX,iconY,iconW,iconH,6,6);
                    bg.setFont(new Font("SansSerif",Font.BOLD,13));
                    FontMetrics fm=bg.getFontMetrics();
                    bg.setColor(new Color(120,120,120));
                    bg.drawString("–",iconX+(iconW-fm.stringWidth("–"))/2,iconY+(iconH+fm.getAscent()-fm.getDescent())/2);
                }
                bg.setFont(new Font("Monospaced",Font.BOLD,10));
                bg.setColor(new Color(200,200,200,180));
                bg.drawString(String.valueOf(i+1),x+4,y+13);
            }
            int selItem=hotbarItems[hotbarSelected];
            String name=selItem>=0?MASTER_NAMES[selItem]:"Leer";
            bg.setFont(new Font("SansSerif",Font.BOLD,13));
            FontMetrics fm=bg.getFontMetrics();
            int nameX=(W-fm.stringWidth(name))/2, nameY=H-8;
            bg.setColor(new Color(0,0,0,140));
            bg.fillRoundRect(nameX-8,nameY-14,fm.stringWidth(name)+16,19,6,6);
            bg.setColor(new Color(255,220,80));
            bg.drawString(name,nameX,nameY);
        }

        static final int S_SIZE=48, S_GAP=5, S_PAD=14;
        int invWinX,invWinY,invWinW,invWinH,hotWinX,hotWinY;

        void drawInventory() {
            int cols=INV_COLS, rows=INV_ROWS;
            invWinW=cols*(S_SIZE+S_GAP)+S_GAP+S_PAD*2;
            invWinH=(rows+1)*(S_SIZE+S_GAP)+S_GAP*3+S_PAD*2+28;
            invWinX=(W-invWinW)/2; invWinY=(H-invWinH)/2;
            int slotOriginX=invWinX+S_PAD, slotOriginY=invWinY+S_PAD+22;
            int sepY=slotOriginY+rows*(S_SIZE+S_GAP)+4;
            hotWinX=slotOriginX; hotWinY=sepY+S_GAP+4;
            bg.setColor(new Color(0,0,0,120)); bg.fillRect(0,0,W,H);
            bg.setColor(new Color(30,30,30,220));
            bg.fillRoundRect(invWinX,invWinY,invWinW,invWinH,14,14);
            bg.setColor(new Color(255,255,255,60));
            bg.drawRoundRect(invWinX,invWinY,invWinW,invWinH,14,14);
            bg.setFont(new Font("SansSerif",Font.BOLD,15));
            bg.setColor(new Color(255,220,80));
            bg.drawString("Inventar",invWinX+S_PAD,invWinY+S_PAD+14);
            bg.setFont(new Font("SansSerif",Font.PLAIN,11));
            bg.setColor(new Color(180,180,180));
            bg.drawString("[E] Schließen  |  Klick = aufnehmen/ablegen",invWinX+S_PAD+80,invWinY+S_PAD+14);
            for (int row=0; row<rows; row++)
                for (int col=0; col<cols; col++) {
                    int idx=row*cols+col;
                    int sx=slotOriginX+col*(S_SIZE+S_GAP), sy=slotOriginY+row*(S_SIZE+S_GAP);
                    drawInvSlot(sx,sy,invSlots[idx],false,idx==dragSource);
                }
            bg.setColor(new Color(255,255,255,40));
            bg.fillRect(slotOriginX,sepY,cols*(S_SIZE+S_GAP)-S_GAP,1);
            for (int col=0; col<HOTBAR_SLOTS; col++) {
                int sx=hotWinX+col*(S_SIZE+S_GAP);
                drawInvSlot(sx,hotWinY,hotbarItems[col],col==hotbarSelected,(INV_ROWS*INV_COLS+col)==dragSource);
                bg.setFont(new Font("Monospaced",Font.BOLD,9));
                bg.setColor(new Color(200,200,200,160));
                bg.drawString(String.valueOf(col+1),sx+3,hotWinY+11);
            }
            if (dragSource>=0&&dragItem>=0) {
                int ix=dragMouseX-S_SIZE/2, iy=dragMouseY-S_SIZE/2;
                bg.setColor(new Color(MASTER_COLORS[dragItem].getRed(),MASTER_COLORS[dragItem].getGreen(),MASTER_COLORS[dragItem].getBlue(),200));
                bg.fillRoundRect(ix+6,iy+6,S_SIZE-12,S_SIZE-12,6,6);
                bg.setFont(new Font("SansSerif",Font.BOLD,12));
                FontMetrics fm=bg.getFontMetrics();
                String icon=MASTER_ICONS[dragItem];
                bg.setColor(Color.WHITE);
                bg.drawString(icon,ix+(S_SIZE-fm.stringWidth(icon))/2,iy+S_SIZE/2+fm.getAscent()/2-2);
            }
        }

        void drawInvSlot(int sx, int sy, int item, boolean selected, boolean dragging) {
            bg.setColor(selected?new Color(80,70,30,180):new Color(50,50,50,160));
            bg.fillRoundRect(sx,sy,S_SIZE,S_SIZE,8,8);
            if (selected) {
                bg.setColor(new Color(255,220,50,180)); bg.setStroke(new BasicStroke(2f));
                bg.drawRoundRect(sx,sy,S_SIZE,S_SIZE,8,8); bg.setStroke(new BasicStroke(1f));
            } else {
                bg.setColor(new Color(255,255,255,35));
                bg.drawRoundRect(sx,sy,S_SIZE,S_SIZE,8,8);
            }
            if (item>=0&&!dragging) {
                int ix=sx+7, iy=sy+7, iw=S_SIZE-14, ih=S_SIZE-14;
                bg.setColor(MASTER_COLORS[item]); bg.fillRoundRect(ix,iy,iw,ih,5,5);
                bg.setColor(new Color(255,255,255,55)); bg.fillRoundRect(ix+2,iy+2,iw-4,ih/2-1,3,3);
                String icon=MASTER_ICONS[item]; int fontSize=icon.length()>2?9:12;
                bg.setFont(new Font("SansSerif",Font.BOLD,fontSize));
                FontMetrics fm=bg.getFontMetrics();
                int tx=ix+(iw-fm.stringWidth(icon))/2, ty=iy+(ih+fm.getAscent()-fm.getDescent())/2;
                bg.setColor(new Color(0,0,0,150)); bg.drawString(icon,tx+1,ty+1);
                bg.setColor(Color.WHITE); bg.drawString(icon,tx,ty);
                bg.setFont(new Font("SansSerif",Font.PLAIN,8)); fm=bg.getFontMetrics();
                String name=MASTER_NAMES[item];
                bg.setColor(new Color(0,0,0,120)); bg.drawString(name,sx+(S_SIZE-fm.stringWidth(name))/2+1,sy+S_SIZE-4);
                bg.setColor(new Color(220,220,220)); bg.drawString(name,sx+(S_SIZE-fm.stringWidth(name))/2,sy+S_SIZE-5);
            }
        }

        int getItemAt(int slot) {
            if (slot<INV_ROWS*INV_COLS) return invSlots[slot];
            return hotbarItems[slot-INV_ROWS*INV_COLS];
        }
        void setItemAt(int slot, int item) {
            if (slot<INV_ROWS*INV_COLS) invSlots[slot]=item;
            else hotbarItems[slot-INV_ROWS*INV_COLS]=item;
        }
        int getSlotAt(int mx, int my) {
            if (!inventoryOpen) return -1;
            int cols=INV_COLS, rows=INV_ROWS;
            int winW=cols*(S_SIZE+S_GAP)+S_GAP+S_PAD*2;
            int winH=(rows+1)*(S_SIZE+S_GAP)+S_GAP*3+S_PAD*2+28;
            int winX=(W-winW)/2, winY=(H-winH)/2;
            int ox=winX+S_PAD, oy=winY+S_PAD+22;
            for (int row=0; row<rows; row++)
                for (int col=0; col<cols; col++) {
                    int sx=ox+col*(S_SIZE+S_GAP), sy=oy+row*(S_SIZE+S_GAP);
                    if (mx>=sx&&mx<sx+S_SIZE&&my>=sy&&my<sy+S_SIZE) return row*cols+col;
                }
            int sepY=oy+rows*(S_SIZE+S_GAP)+4, hotY=sepY+S_GAP+4;
            for (int col=0; col<HOTBAR_SLOTS; col++) {
                int sx=ox+col*(S_SIZE+S_GAP);
                if (mx>=sx&&mx<sx+S_SIZE&&my>=hotY&&my<hotY+S_SIZE) return rows*cols+col;
            }
            return -1;
        }

        void collectBerries() {
            final double REACH=2.5;
            int collected=0;
            for (int i=0; i<berryCount; i++) {
                if (!berryAlive[i]) continue;
                double dx=berryX[i]-camX, dy=berryY[i]-camY, dz=berryZ[i]-camZ;
                if (dx*dx+dy*dy+dz*dz<REACH*REACH) { berryAlive[i]=false; addToInventory(ITEM_BEERE); collected++; }
            }
            for (int i=0; i<gItemCount; i++) {
                if (!gItemAlive[i]) continue;
                double dx=gItemX[i]-camX, dy=gItemY[i]-camY, dz=gItemZ[i]-camZ;
                if (dx*dx+dy*dy+dz*dz<REACH*REACH) { gItemAlive[i]=false; addToInventory(gItemType[i]); collected++; }
            }
            if (collected>0) {
                collectMsg="+" + collected+" Gegenstand"+(collected>1?"e":"")+" eingesammelt!";
                collectTimer=120; rebuildBerryTriangles();
            } else { collectMsg="Nichts in der Nähe."; collectTimer=80; }
        }

        void addToInventory(int itemIdx) {
            for (int i=0; i<invSlots.length; i++) { if (invSlots[i]<0) { invSlots[i]=itemIdx; return; } }
            for (int i=0; i<hotbarItems.length; i++) { if (hotbarItems[i]<0) { hotbarItems[i]=itemIdx; return; } }
        }

        void rebuildBerryTriangles() {}

        int craftBtnX, craftBtnY, craftBtnW=130, craftBtnH=32;

        void drawCraftingBook() {
            bg.setColor(new Color(0,0,0,140)); bg.fillRect(0,0,W,H);
            int bW=700, bH=440, bX=(W-bW)/2, bY=(H-bH)/2;
            bg.setColor(new Color(60,40,20,180)); bg.fillRoundRect(bX+6,bY+6,bW,bH,18,18);
            bg.setColor(new Color(245,235,210)); bg.fillRoundRect(bX,bY,bW,bH,18,18);
            int spineX=bX+bW/2;
            bg.setColor(new Color(160,110,60)); bg.fillRect(spineX-8,bY,16,bH);
            bg.setColor(new Color(120,80,40));
            for (int ly=bY+30; ly<bY+bH-20; ly+=40) bg.fillRect(spineX-8,ly,16,3);
            bg.setColor(new Color(210,195,170)); bg.fillRect(bX,bY,6,bH); bg.fillRect(bX+bW-6,bY,6,bH);
            bg.setColor(new Color(140,100,55)); bg.setStroke(new BasicStroke(3f));
            bg.drawRoundRect(bX,bY,bW,bH,18,18); bg.setStroke(new BasicStroke(1f));

            int lX=bX+18, lY=bY+18, lW=bW/2-30;
            bg.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,17));
            bg.setColor(new Color(90,55,20));
            bg.drawString("✦ Rezepte ✦",lX+lW/2-52,lY+20);
            bg.setColor(new Color(180,150,100)); bg.fillRect(lX,lY+26,lW,1);
            int entryH=52, entryGap=6, listY=lY+34;
            for (int r=0; r<RECIPE_NAMES.length; r++) {
                int ey=listY+r*(entryH+entryGap); boolean sel=(r==craftSelected);
                if (sel) {
                    bg.setColor(new Color(200,160,90,180)); bg.fillRoundRect(lX,ey,lW,entryH,10,10);
                    bg.setColor(new Color(140,100,40)); bg.setStroke(new BasicStroke(2f));
                    bg.drawRoundRect(lX,ey,lW,entryH,10,10); bg.setStroke(new BasicStroke(1f));
                } else {
                    bg.setColor(new Color(220,200,165,120)); bg.fillRoundRect(lX,ey,lW,entryH,10,10);
                }
                int res=RECIPE_RESULT[r];
                bg.setColor(MASTER_COLORS[res]); bg.fillRoundRect(lX+6,ey+8,34,34,7,7);
                bg.setColor(new Color(255,255,255,60)); bg.fillRoundRect(lX+8,ey+10,30,14,4,4);
                bg.setFont(new Font("SansSerif",Font.BOLD,11)); FontMetrics fm=bg.getFontMetrics();
                String icon=MASTER_ICONS[res]; bg.setColor(Color.WHITE);
                bg.drawString(icon,lX+6+(34-fm.stringWidth(icon))/2,ey+8+22);
                bg.setFont(new Font("Serif",Font.BOLD,14));
                bg.setColor(sel?new Color(60,35,10):new Color(80,55,25)); bg.drawString(RECIPE_NAMES[r],lX+48,ey+22);
                bg.setFont(new Font("SansSerif",Font.PLAIN,10)); bg.setColor(new Color(110,80,40));
                StringBuilder sb=new StringBuilder();
                for (int[] ingr:RECIPE_INGR[r]) { if (sb.length()>0) sb.append(" + "); sb.append(ingr[1]).append("x ").append(MASTER_NAMES[ingr[0]]); }
                bg.drawString(sb.toString(),lX+48,ey+38);
            }
            bg.setFont(new Font("Serif",Font.ITALIC,11)); bg.setColor(new Color(140,110,70));
            bg.drawString("1",lX+lW/2-3,bY+bH-10);

            int rX=spineX+18, rY=bY+18, rW=bW/2-30, r=craftSelected;
            bg.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,17)); bg.setColor(new Color(90,55,20));
            String rtitle="✦ "+RECIPE_NAMES[r]+" ✦";
            bg.drawString(rtitle,rX+(rW-bg.getFontMetrics().stringWidth(rtitle))/2,rY+20);
            bg.setColor(new Color(180,150,100)); bg.fillRect(rX,rY+26,rW,1);
            int bigSize=64, bigX=rX+(rW-bigSize)/2, bigY=rY+36, res=RECIPE_RESULT[r];
            bg.setColor(MASTER_COLORS[res]); bg.fillRoundRect(bigX,bigY,bigSize,bigSize,12,12);
            bg.setColor(new Color(255,255,255,70)); bg.fillRoundRect(bigX+4,bigY+4,bigSize-8,bigSize/2-4,8,8);
            bg.setFont(new Font("SansSerif",Font.BOLD,22)); FontMetrics fm=bg.getFontMetrics();
            String icon=MASTER_ICONS[res]; bg.setColor(Color.WHITE);
            bg.drawString(icon,bigX+(bigSize-fm.stringWidth(icon))/2,bigY+bigSize/2+10);
            bg.setFont(new Font("Serif",Font.BOLD,13)); fm=bg.getFontMetrics();
            bg.setColor(new Color(70,45,15));
            bg.drawString(MASTER_NAMES[res],rX+(rW-fm.stringWidth(MASTER_NAMES[res]))/2,bigY+bigSize+16);
            bg.setFont(new Font("Serif",Font.ITALIC,12)); bg.setColor(new Color(90,65,30));
            String[] descLines=RECIPE_DESC[r].split("\n"); int descY=bigY+bigSize+32;
            for (String line:descLines) { bg.drawString(line,rX+(rW-bg.getFontMetrics().stringWidth(line))/2,descY); descY+=16; }
            descY+=8;
            bg.setFont(new Font("Serif",Font.BOLD,13)); bg.setColor(new Color(90,55,20));
            bg.drawString("Zutaten:",rX+10,descY); descY+=4;
            bg.setColor(new Color(180,150,100)); bg.fillRect(rX,descY,rW,1); descY+=10;
            int slotSz=38, totalW=RECIPE_INGR[r].length*(slotSz+8)-8, slotStartX=rX+(rW-totalW)/2;
            for (int j=0; j<RECIPE_INGR[r].length; j++) {
                int itemIdx=RECIPE_INGR[r][j][0], needed=RECIPE_INGR[r][j][1], have=countInInventory(itemIdx);
                boolean enough=have>=needed; int sx=slotStartX+j*(slotSz+8);
                bg.setColor(enough?new Color(180,220,160,150):new Color(220,160,160,150));
                bg.fillRoundRect(sx,descY,slotSz,slotSz,8,8);
                bg.setColor(enough?new Color(80,150,60):new Color(180,60,60));
                bg.setStroke(new BasicStroke(1.5f)); bg.drawRoundRect(sx,descY,slotSz,slotSz,8,8); bg.setStroke(new BasicStroke(1f));
                bg.setColor(MASTER_COLORS[itemIdx]); bg.fillRoundRect(sx+5,descY+5,slotSz-10,slotSz-10,5,5);
                bg.setFont(new Font("SansSerif",Font.BOLD,10)); fm=bg.getFontMetrics();
                String ic=MASTER_ICONS[itemIdx]; bg.setColor(Color.WHITE);
                bg.drawString(ic,sx+5+(slotSz-10-fm.stringWidth(ic))/2,descY+slotSz/2+4);
                bg.setFont(new Font("SansSerif",Font.BOLD,10));
                bg.setColor(enough?new Color(40,120,40):new Color(160,40,40));
                String cnt=have+"/"+needed; fm=bg.getFontMetrics();
                bg.drawString(cnt,sx+(slotSz-fm.stringWidth(cnt))/2,descY+slotSz+13);
                bg.setFont(new Font("SansSerif",Font.PLAIN,9)); fm=bg.getFontMetrics();
                String nm=MASTER_NAMES[itemIdx]; bg.setColor(new Color(80,55,25));
                bg.drawString(nm,sx+(slotSz-fm.stringWidth(nm))/2,descY+slotSz+24);
            }
            boolean canCraft=canCraftRecipe(r);
            craftBtnX=rX+(rW-craftBtnW)/2; craftBtnY=bY+bH-55;
            bg.setColor(canCraft?new Color(80,150,60):new Color(130,130,130));
            bg.fillRoundRect(craftBtnX,craftBtnY,craftBtnW,craftBtnH,12,12);
            bg.setColor(canCraft?new Color(50,100,40):new Color(90,90,90));
            bg.setStroke(new BasicStroke(2f)); bg.drawRoundRect(craftBtnX,craftBtnY,craftBtnW,craftBtnH,12,12); bg.setStroke(new BasicStroke(1f));
            bg.setFont(new Font("Serif",Font.BOLD,14)); fm=bg.getFontMetrics();
            String btnTxt=canCraft?"✦ Herstellen ✦":"Fehlt: Zutaten";
            bg.setColor(Color.WHITE);
            bg.drawString(btnTxt,craftBtnX+(craftBtnW-fm.stringWidth(btnTxt))/2,craftBtnY+21);
            if (craftFeedTimer>0) {
                craftFeedTimer--;
                float alpha=Math.min(1f,craftFeedTimer/20f);
                bg.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,13)); fm=bg.getFontMetrics();
                int fw=fm.stringWidth(craftFeedback);
                bg.setColor(new Color(0,0,0,(int)(120*alpha)));
                bg.fillRoundRect(craftBtnX+(craftBtnW-fw)/2-6,craftBtnY-26,fw+12,20,6,6);
                bg.setColor(new Color(80,200,80,(int)(255*alpha)));
                bg.drawString(craftFeedback,craftBtnX+(craftBtnW-fw)/2,craftBtnY-11);
            }
            bg.setFont(new Font("Serif",Font.ITALIC,11)); bg.setColor(new Color(140,110,70));
            bg.drawString("2",rX+rW/2,bY+bH-10);
            bg.setFont(new Font("SansSerif",Font.PLAIN,11)); bg.setColor(new Color(140,110,70));
            bg.drawString("[Q] Schließen",bX+bW/2-42,bY+bH-10);
        }

        boolean canCraftRecipe(int r) {
            for (int[] ingr:RECIPE_INGR[r]) if (countInInventory(ingr[0])<ingr[1]) return false;
            return true;
        }
        int countInInventory(int itemIdx) {
            int count=0;
            for (int s:invSlots) if (s==itemIdx) count++;
            for (int s:hotbarItems) if (s==itemIdx) count++;
            return count;
        }
        void tryCraft(int r) {
            if (!canCraftRecipe(r)) return;
            for (int[] ingr:RECIPE_INGR[r]) {
                int toRemove=ingr[1];
                for (int i=0; i<invSlots.length&&toRemove>0; i++) if (invSlots[i]==ingr[0]) { invSlots[i]=-1; toRemove--; }
                for (int i=0; i<hotbarItems.length&&toRemove>0; i++) if (hotbarItems[i]==ingr[0]) { hotbarItems[i]=-1; toRemove--; }
            }
            addToInventory(RECIPE_RESULT[r]);
            craftFeedback=RECIPE_NAMES[r]+" hergestellt!"; craftFeedTimer=90;
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.drawImage(buffer,0,0,getWidth(),getHeight(),null);
        }

        // ──────────────────────────────────────────
        //  Tastatur
        // ──────────────────────────────────────────
        public void keyPressed(KeyEvent e) {
            // Menü-Tasten
            if (gameState.equals("MENU")) {
                if (e.getKeyCode()==KeyEvent.VK_ENTER) { gameState="GAME"; lastMouseX=-1; lastMouseY=-1; }
                if (e.getKeyCode()==KeyEvent.VK_H)     { gameState="HELP"; }
                return;
            }
            if (gameState.equals("HELP")) {
                if (e.getKeyCode()==KeyEvent.VK_ESCAPE||e.getKeyCode()==KeyEvent.VK_BACK_SPACE||e.getKeyCode()==KeyEvent.VK_H)
                    gameState="MENU";
                return;
            }

            // Spieltasten
            switch (e.getKeyCode()) {
                case KeyEvent.VK_F11: frame.toggleFullscreen(); break;
                case KeyEvent.VK_Q:
                    craftingOpen=!craftingOpen;
                    if (craftingOpen) inventoryOpen=false;
                    lastMouseX=-1; lastMouseY=-1; break;
                case KeyEvent.VK_E:
                    inventoryOpen=!inventoryOpen;
                    if (inventoryOpen) craftingOpen=false;
                    if (!inventoryOpen) { dragSource=-1; dragItem=-1; }
                    lastMouseX=-1; lastMouseY=-1; break;
                case KeyEvent.VK_C:
                    if (!inventoryOpen&&!craftingOpen) collectBerries(); break;
                case KeyEvent.VK_W: case KeyEvent.VK_UP:    if (!inventoryOpen&&!craftingOpen) keyW=true; break;
                case KeyEvent.VK_S: case KeyEvent.VK_DOWN:  if (!inventoryOpen&&!craftingOpen) keyS=true; break;
                case KeyEvent.VK_A: case KeyEvent.VK_LEFT:  if (!inventoryOpen&&!craftingOpen) keyA=true; break;
                case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: if (!inventoryOpen&&!craftingOpen) keyD=true; break;
                case KeyEvent.VK_1: if (!inventoryOpen&&!craftingOpen) hotbarSelected=0; break;
                case KeyEvent.VK_2: if (!inventoryOpen&&!craftingOpen) hotbarSelected=1; break;
                case KeyEvent.VK_3: if (!inventoryOpen&&!craftingOpen) hotbarSelected=2; break;
                case KeyEvent.VK_4: if (!inventoryOpen&&!craftingOpen) hotbarSelected=3; break;
                case KeyEvent.VK_5: if (!inventoryOpen&&!craftingOpen) hotbarSelected=4; break;
                case KeyEvent.VK_6: if (!inventoryOpen&&!craftingOpen) hotbarSelected=5; break;
                case KeyEvent.VK_7: if (!inventoryOpen&&!craftingOpen) hotbarSelected=6; break;
                case KeyEvent.VK_8: if (!inventoryOpen&&!craftingOpen) hotbarSelected=7; break;
                case KeyEvent.VK_9: if (!inventoryOpen&&!craftingOpen) hotbarSelected=8; break;
                // Im Spiel auch ESC → zurück zum Menü
                case KeyEvent.VK_ESCAPE:
                    if (!inventoryOpen&&!craftingOpen) {
                        gameState="MENU"; keyW=keyS=keyA=keyD=false;
                    }
                    break;
            }
        }
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_W: case KeyEvent.VK_UP:    keyW=false; break;
                case KeyEvent.VK_S: case KeyEvent.VK_DOWN:  keyS=false; break;
                case KeyEvent.VK_A: case KeyEvent.VK_LEFT:  keyA=false; break;
                case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: keyD=false; break;
            }
        }
        public void keyTyped(KeyEvent e) {}
    }

    // ══════════════════════════════════════════════
    //  Dreieck-Datenstruktur
    // ══════════════════════════════════════════════
    static class Triangle {
        double x0,y0,z0, x1,y1,z1, x2,y2,z2, cx,cy,cz;
        Color color;
        Triangle(double x0,double y0,double z0,double x1,double y1,double z1,double x2,double y2,double z2,Color color) {
            this.x0=x0;this.y0=y0;this.z0=z0;this.x1=x1;this.y1=y1;this.z1=z1;this.x2=x2;this.y2=y2;this.z2=z2;this.color=color;
            this.cx=(x0+x1+x2)/3.0;this.cy=(y0+y1+y2)/3.0;this.cz=(z0+z1+z2)/3.0;
        }
    }
}
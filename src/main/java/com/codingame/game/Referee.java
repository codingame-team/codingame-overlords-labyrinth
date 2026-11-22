package com.codingame.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import com.codingame.gameengine.core.AbstractPlayer.TimeoutException;
import com.codingame.gameengine.core.AbstractReferee;
import com.codingame.gameengine.core.GameManager;
import com.codingame.gameengine.core.MultiplayerGameManager;
import com.codingame.gameengine.module.endscreen.EndScreenModule;
import com.codingame.gameengine.module.entities.Circle;
import com.codingame.gameengine.module.entities.Curve;
import com.codingame.gameengine.module.entities.GraphicEntityModule;
import com.codingame.gameengine.module.entities.Group;
import com.codingame.gameengine.module.entities.Polygon;
import com.codingame.gameengine.module.entities.Rectangle;
import com.codingame.gameengine.module.entities.Sprite;
import com.codingame.gameengine.module.entities.Text;
import com.codingame.gameengine.module.toggle.ToggleModule;
import com.codingame.gameengine.module.tooltip.TooltipModule;
import com.google.inject.Inject;

public class Referee extends AbstractReferee {
    public static final int WIDTH = 11;
    public static final int HEIGHT = 11;
    public static final int MAX_TURNS = 100;
    private static final int CELL_SIZE = 76;
    private static final int ORIGIN_X = (1920 - WIDTH * CELL_SIZE) / 2;
    private static final int ORIGIN_Y = (1080 - HEIGHT * CELL_SIZE) / 2 + 25;
    private static final int MAX_MINION_SLOTS = 12;

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};

    public enum MinionType {
        HOUND(2, 12, 6, 1, "minion_hound.png", "H"),
        GUARD(3, 24, 8, 1, "monster_skeleton.png", "G"),
        WRAITH(4, 16, 7, 2, "minion_wraith.png", "W");

        public final int cost;
        public final int maxHp;
        public final int atk;
        public final int range;
        public final String sprite;
        public final String code;

        MinionType(int cost, int maxHp, int atk, int range, String sprite, String code) {
            this.cost = cost;
            this.maxHp = maxHp;
            this.atk = atk;
            this.range = range;
            this.sprite = sprite;
            this.code = code;
        }
    }

    public static class Pentagram {
        int id;
        int x, y;
        int owner; // -1=neutral, 0=p0, 1=p1
        Circle auraCircle;
        Polygon debugHex;
        Group group;
    }

    public static class MinionSlot {
        int id;
        boolean active;
        Player owner;
        MinionType type;
        int x, y;
        int hp;
        int activationsThisTurn;

        // Dual-Mode PixiJS Graphics (pre-allocated pool)
        Group rootGroup;
        Circle aura;
        Sprite sprite;
        Rectangle hpBar;
        Polygon debugShape;
        Text debugText;
    }

    @Inject private MultiplayerGameManager<Player> gameManager;
    @Inject private GraphicEntityModule graphicEntityModule;
    @Inject private TooltipModule tooltipModule;
    @Inject private EndScreenModule endScreenModule;
    @Inject private ToggleModule toggleModule;

    private final char[][] grid = new char[HEIGHT][WIDTH];
    private final List<Pentagram> pentagrams = new ArrayList<>();
    private final List<MinionSlot> minionPool = new ArrayList<>();
    private Text turnText;
    private int leagueLevel;
    private int nextMinionId = 0;

    private int cellX(int x) {
        return ORIGIN_X + x * CELL_SIZE + CELL_SIZE / 2;
    }

    private int cellY(int y) {
        return ORIGIN_Y + y * CELL_SIZE + CELL_SIZE / 2;
    }

    @Override
    public void init() {
        Random rng = gameManager.getRandom();
        leagueLevel = Math.max(1, gameManager.getLeagueLevel());

        gameManager.setMaxTurns(MAX_TURNS);
        gameManager.setFirstTurnMaxTime(1000);
        gameManager.setTurnMaxTime(50);
        gameManager.setFrameDuration(550);

        Player p0 = gameManager.getPlayer(0);
        Player p1 = gameManager.getPlayer(1);
        p0.setSanctumPos(1, 5);
        p1.setSanctumPos(WIDTH - 2, 5);

        generateLabyrinth(rng);
        initGraphics();

        // Spawn 1 initial GUARD for each Overlord
        spawnMinion(p0, MinionType.GUARD, 2, 5);
        spawnMinion(p1, MinionType.GUARD, WIDTH - 3, 5);
        refreshGraphics();

        for (Player player : gameManager.getActivePlayers()) {
            Player opp = gameManager.getPlayer(1 - player.getIndex());
            player.sendInputLine(String.format("%d %d %d", WIDTH, HEIGHT, leagueLevel));
            player.sendInputLine(String.format("%d %d %d %d",
                player.getSanctumX(), player.getSanctumY(), opp.getSanctumX(), opp.getSanctumY()));
        }
    }

    private void generateLabyrinth(Random rng) {
        int mid = WIDTH / 2;
        for (int attempt = 0; attempt < 40; attempt++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) grid[y][x] = '.';
            }
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x <= mid; x++) {
                    if (x == mid && y > HEIGHT / 2) continue;
                    if (rng.nextDouble() < 0.22) {
                        grid[y][x] = '#';
                        grid[HEIGHT - 1 - y][WIDTH - 1 - x] = '#';
                    }
                }
            }
            // Keep Sanctums and initial guard spawns open
            grid[5][1] = '.'; grid[5][2] = '.';
            grid[5][WIDTH - 2] = '.'; grid[5][WIDTH - 3] = '.';
            grid[mid][mid] = '.';

            int sideX = 3 + rng.nextInt(3); // 3..5
            int sideY = 1 + rng.nextInt(3); // 1..3

            pentagrams.clear();
            int[][] pCoords = {
                {1, 5, 0},
                {WIDTH - 2, 5, 1},
                {mid, mid, -1},
                {sideX, sideY, -1},
                {WIDTH - 1 - sideX, HEIGHT - 1 - sideY, -1}
            };
            for (int i = 0; i < pCoords.length; i++) {
                Pentagram p = new Pentagram();
                p.id = i;
                p.x = pCoords[i][0];
                p.y = pCoords[i][1];
                p.owner = pCoords[i][2];
                grid[p.y][p.x] = '.';
                pentagrams.add(p);
            }

            if (isLabyrinthConnected()) {
                return;
            }
        }
        for (int i = 1; i < WIDTH - 1; i++) {
            grid[mid][i] = '.';
            grid[i][mid] = '.';
        }
    }

    private boolean isLabyrinthConnected() {
        boolean[][] vis = new boolean[HEIGHT][WIDTH];
        Queue<int[]> q = new ArrayDeque<>();
        vis[5][1] = true;
        q.add(new int[]{1, 5});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DX[d], ny = c[1] + DY[d];
                if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT && grid[ny][nx] == '.' && !vis[ny][nx]) {
                    vis[ny][nx] = true;
                    q.add(new int[]{nx, ny});
                }
            }
        }
        for (Pentagram p : pentagrams) {
            if (!vis[p.y][p.x]) return false;
        }
        return true;
    }

    private void initGraphics() {
        graphicEntityModule.createSprite()
            .setImage("background.jpg")
            .setBaseWidth(1920)
            .setBaseHeight(1080)
            .setZIndex(-20);

        graphicEntityModule.createRectangle()
            .setX(ORIGIN_X - 14)
            .setY(ORIGIN_Y - 14)
            .setWidth(WIDTH * CELL_SIZE + 28)
            .setHeight(HEIGHT * CELL_SIZE + 28)
            .setFillColor(0x0b0e17)
            .setAlpha(0.90)
            .setLineWidth(4)
            .setLineColor(0x553c9a)
            .setZIndex(-15);

        graphicEntityModule.createText("OVERLORD'S LABYRINTH")
            .setX(960)
            .setY(40)
            .setAnchor(0.5)
            .setFontFamily("Lato")
            .setFontSize(42)
            .setFillColor(0xd6bcfa)
            .setStrokeThickness(4)
            .setStrokeColor(0x000000)
            .setZIndex(30);

        turnText = graphicEntityModule.createText("TURN 0 / " + MAX_TURNS)
            .setX(960)
            .setY(92)
            .setAnchor(0.5)
            .setFontFamily("Lato")
            .setFontSize(36)
            .setFillColor(0xf6e05e)
            .setZIndex(30);

        for (Player p : gameManager.getPlayers()) {
            int idx = p.getIndex();
            int hudX = idx == 0 ? 235 : 1685;

            graphicEntityModule.createRectangle()
                .setX(hudX - 195)
                .setY(180)
                .setWidth(390)
                .setHeight(330)
                .setFillColor(0x171426)
                .setAlpha(0.90)
                .setLineWidth(4)
                .setLineColor(p.getColorToken())
                .setZIndex(20);

            graphicEntityModule.createSprite()
                .setImage(p.getAvatarToken())
                .setX(hudX)
                .setY(250)
                .setAnchor(0.5)
                .setBaseWidth(86)
                .setBaseHeight(86)
                .setZIndex(22);

            graphicEntityModule.createText(p.getNicknameToken())
                .setX(hudX)
                .setY(325)
                .setAnchor(0.5)
                .setFontFamily("Lato")
                .setFontSize(36)
                .setFillColor(p.getColorToken())
                .setZIndex(22);

            Text scoreTxt = graphicEntityModule.createText("SCORE: 0")
                .setX(hudX)
                .setY(390)
                .setAnchor(0.5)
                .setFontFamily("Lato")
                .setFontSize(40)
                .setFillColor(0xf6e05e)
                .setZIndex(22);
            p.setScoreText(scoreTxt);

            Text statusTxt = graphicEntityModule.createText("SANCTUM: 40 HP")
                .setX(hudX)
                .setY(448)
                .setAnchor(0.5)
                .setFontFamily("Lato")
                .setFontSize(36)
                .setFillColor(0xd6bcfa)
                .setZIndex(22);
            p.setStatusText(statusTxt);
        }

        // Tiles (Dual-Mode)
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int cx = cellX(x), cy = cellY(y);
                boolean isWall = grid[y][x] == '#';

                String img = isWall ? "pillar.png" : (((x + y) % 2 == 0) ? "floor1.png" : "floor2.png");
                Sprite ts = graphicEntityModule.createSprite()
                    .setImage(img)
                    .setX(cx)
                    .setY(cy)
                    .setAnchor(0.5)
                    .setBaseWidth(CELL_SIZE - 2)
                    .setBaseHeight(CELL_SIZE - 2)
                    .setZIndex(1);
                toggleModule.displayOnToggleState(ts, "renderMode", true);

                Rectangle dt = graphicEntityModule.createRectangle()
                    .setX(cx - CELL_SIZE / 2 + 2)
                    .setY(cy - CELL_SIZE / 2 + 2)
                    .setWidth(CELL_SIZE - 4)
                    .setHeight(CELL_SIZE - 4)
                    .setFillColor(isWall ? 0x3b4252 : 0x151824)
                    .setLineWidth(2)
                    .setLineColor(isWall ? 0x81a1c1 : 0x2a2e3d)
                    .setZIndex(1);
                toggleModule.displayOnToggleState(dt, "renderMode", false);
            }
        }

        // Pentagrams (Dual-Mode)
        for (Pentagram p : pentagrams) {
            Circle aura = graphicEntityModule.createCircle()
                .setRadius(30)
                .setFillColor(0x9f7aea)
                .setFillAlpha(0.28)
                .setLineWidth(4)
                .setLineColor(0x9f7aea);
            Sprite rune = graphicEntityModule.createSprite()
                .setImage("fx_heal.png")
                .setAnchor(0.5)
                .setBaseWidth(52)
                .setBaseHeight(52);
            Group spView = graphicEntityModule.createGroup(aura, rune);
            toggleModule.displayOnToggleState(spView, "renderMode", true);

            Polygon hex = graphicEntityModule.createPolygon()
                .addPoint(0, -28).addPoint(24, -14).addPoint(24, 14)
                .addPoint(0, 28).addPoint(-24, 14).addPoint(-24, -14)
                .setFillColor(0x553c9a)
                .setLineWidth(3)
                .setLineColor(0xffffff);
            toggleModule.displayOnToggleState(hex, "renderMode", false);

            p.auraCircle = aura;
            p.debugHex = hex;
            p.group = graphicEntityModule.createGroup(spView, hex)
                .setX(cellX(p.x))
                .setY(cellY(p.y))
                .setZIndex(4);
        }

        // Pre-allocate Minion Pool (Dual-Mode: RPG Sprites vs Geometric Shapes)
        for (int i = 0; i < MAX_MINION_SLOTS; i++) {
            MinionSlot slot = new MinionSlot();
            slot.active = false;

            slot.aura = graphicEntityModule.createCircle()
                .setRadius(25)
                .setFillAlpha(0.35)
                .setLineWidth(3);
            slot.sprite = graphicEntityModule.createSprite()
                .setImage("monster_skeleton.png")
                .setAnchor(0.5)
                .setBaseWidth(54)
                .setBaseHeight(54);
            slot.hpBar = graphicEntityModule.createRectangle()
                .setX(-22)
                .setY(-34)
                .setWidth(44)
                .setHeight(6)
                .setFillColor(0x48bb78);
            Group spView = graphicEntityModule.createGroup(slot.aura, slot.sprite, slot.hpBar);
            toggleModule.displayOnToggleState(spView, "renderMode", true);

            slot.debugShape = graphicEntityModule.createPolygon()
                .addPoint(0, -24).addPoint(24, 0).addPoint(0, 24).addPoint(-24, 0)
                .setLineWidth(3)
                .setLineColor(0xffffff);
            slot.debugText = graphicEntityModule.createText("G")
                .setAnchor(0.5)
                .setFontFamily("Lato")
                .setFontSize(20)
                .setFillColor(0xffffff)
                .setStrokeThickness(2)
                .setStrokeColor(0x000000);
            Group dbgView = graphicEntityModule.createGroup(slot.debugShape, slot.debugText);
            toggleModule.displayOnToggleState(dbgView, "renderMode", false);

            slot.rootGroup = graphicEntityModule.createGroup(spView, dbgView)
                .setVisible(false)
                .setZIndex(10);
            minionPool.add(slot);
        }
    }

    private MinionSlot getMinionAt(int x, int y) {
        for (MinionSlot m : minionPool) {
            if (m.active && m.x == x && m.y == y) return m;
        }
        return null;
    }

    private MinionSlot getMinionById(int id) {
        for (MinionSlot m : minionPool) {
            if (m.active && m.id == id) return m;
        }
        return null;
    }

    private boolean spawnMinion(Player owner, MinionType type, int x, int y) {
        if (getMinionAt(x, y) != null) return false;
        for (MinionSlot slot : minionPool) {
            if (!slot.active) {
                slot.active = true;
                slot.id = nextMinionId++;
                slot.owner = owner;
                slot.type = type;
                slot.x = x;
                slot.y = y;
                slot.hp = type.maxHp;
                slot.activationsThisTurn = 0;

                int col = owner.getColorToken();
                slot.aura.setFillColor(col).setLineColor(col);
                slot.sprite.setImage(type.sprite);
                slot.debugShape.setFillColor(col);
                slot.rootGroup.setX(cellX(x)).setY(cellY(y)).setVisible(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public void gameTurn(int turn) {
        turnText.setText("TURN " + turn + " / " + MAX_TURNS);

        for (MinionSlot m : minionPool) {
            if (m.active) m.activationsThisTurn = 0;
        }

        // Grant Energy Gems to each Overlord (4 base + 1 per controlled Pentagram)
        for (Player p : gameManager.getActivePlayers()) {
            int pIdx = p.getIndex();
            int ownedPents = 0;
            for (Pentagram pent : pentagrams) {
                if (pent.owner == pIdx) ownedPents++;
            }
            p.setGems(4 + ownedPents);
        }

        for (Player player : gameManager.getActivePlayers()) {
            sendTurnInput(player);
            player.execute();
        }

        for (Player player : gameManager.getActivePlayers()) {
            try {
                String line = player.getOutputs().get(0).trim();
                executeOverlordCommands(player, line);
            } catch (TimeoutException e) {
                player.deactivate(player.getNicknameToken() + " timed out!");
                player.setScore(-1);
            }
        }

        if (gameManager.getActivePlayers().size() < 2) {
            gameManager.endGame();
            return;
        }

        resolveCombatAndScoring();
        refreshGraphics();
        checkEndConditions();
    }

    private void sendTurnInput(Player player) {
        int myIdx = player.getIndex();
        Player opp = gameManager.getPlayer(1 - myIdx);
        player.sendInputLine(String.format("%d %d %d", player.getGems(), player.getSanctumHp(), opp.getSanctumHp()));

        for (int y = 0; y < HEIGHT; y++) {
            player.sendInputLine(new String(grid[y]));
        }

        player.sendInputLine(String.valueOf(pentagrams.size()));
        for (Pentagram p : pentagrams) {
            int relOwner = (p.owner == -1) ? -1 : ((p.owner == myIdx) ? 0 : 1);
            player.sendInputLine(String.format("%d %d %d %d", p.id, p.x, p.y, relOwner));
        }

        List<MinionSlot> activeMinions = new ArrayList<>();
        for (MinionSlot m : minionPool) {
            if (m.active && m.owner.getIndex() == myIdx) activeMinions.add(m);
        }
        for (MinionSlot m : minionPool) {
            if (m.active && m.owner.getIndex() != myIdx) activeMinions.add(m);
        }

        player.sendInputLine(String.valueOf(activeMinions.size()));
        for (MinionSlot m : activeMinions) {
            int relOwner = (m.owner.getIndex() == myIdx) ? 0 : 1;
            player.sendInputLine(String.format("%d %d %s %d %d %d",
                m.id, relOwner, m.type.name(), m.x, m.y, m.hp));
        }
    }

    private int[] nextStep(int sx, int sy, int tx, int ty) {
        if (sx == tx && sy == ty) return new int[]{sx, sy};
        int[][] dist = new int[HEIGHT][WIDTH];
        int[][][] first = new int[HEIGHT][WIDTH][2];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) dist[y][x] = -1;
        }
        Queue<int[]> q = new ArrayDeque<>();
        dist[sy][sx] = 0;
        q.add(new int[]{sx, sy});
        int[] best = {sx, sy};
        int bestM = Math.abs(sx - tx) + Math.abs(sy - ty);

        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int cx = cur[0], cy = cur[1];
            if (cx == tx && cy == ty) return first[cy][cx];
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d], ny = cy + DY[d];
                if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT && grid[ny][nx] == '.' && dist[ny][nx] == -1) {
                    dist[ny][nx] = dist[cy][cx] + 1;
                    first[ny][nx][0] = (cx == sx && cy == sy) ? nx : first[cy][cx][0];
                    first[ny][nx][1] = (cx == sx && cy == sy) ? ny : first[cy][cx][1];
                    q.add(new int[]{nx, ny});
                    int m = Math.abs(nx - tx) + Math.abs(ny - ty);
                    if (m < bestM) {
                        bestM = m;
                        best = first[ny][nx];
                    }
                }
            }
        }
        return best;
    }

    private void executeOverlordCommands(Player player, String line) {
        if (line.isEmpty() || "WAIT".equalsIgnoreCase(line)) return;
        String[] actions = line.split(";");
        for (String raw : actions) {
            String[] tokens = raw.trim().split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) continue;
            String cmd = tokens[0].toUpperCase();
            try {
                if ("SUMMON".equals(cmd) && tokens.length >= 3) {
                    int pid = Integer.parseInt(tokens[1]);
                    MinionType type = MinionType.valueOf(tokens[2].toUpperCase());
                    if (type == MinionType.WRAITH && leagueLevel < 2) continue;
                    if (pid >= 0 && pid < pentagrams.size()) {
                        Pentagram pent = pentagrams.get(pid);
                        if (pent.owner == player.getIndex() && player.getGems() >= type.cost) {
                            if (spawnMinion(player, type, pent.x, pent.y)) {
                                player.setGems(player.getGems() - type.cost);
                            }
                        }
                    }
                } else if ("ACTIVATE".equals(cmd) && tokens.length >= 4) {
                    int mid = Integer.parseInt(tokens[1]);
                    int tx = Integer.parseInt(tokens[2]);
                    int ty = Integer.parseInt(tokens[3]);
                    MinionSlot m = getMinionById(mid);
                    if (m != null && m.owner == player) {
                        int maxActivations = (leagueLevel >= 3) ? 2 : 1;
                        int gemCost = (m.activationsThisTurn == 0) ? 1 : 2;
                        if (m.activationsThisTurn < maxActivations && player.getGems() >= gemCost) {
                            player.setGems(player.getGems() - gemCost);
                            m.activationsThisTurn++;
                            int steps = (m.type == MinionType.HOUND) ? 2 : 1;
                            for (int s = 0; s < steps; s++) {
                                int[] step = nextStep(m.x, m.y, tx, ty);
                                if (getMinionAt(step[0], step[1]) == null) {
                                    m.x = step[0];
                                    m.y = step[1];
                                }
                            }
                            m.rootGroup.setX(cellX(m.x), Curve.EASE_IN_AND_OUT)
                                       .setY(cellY(m.y), Curve.EASE_IN_AND_OUT);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void resolveCombatAndScoring() {
        // 1. Capture Pentagrams
        for (Pentagram p : pentagrams) {
            MinionSlot occ = getMinionAt(p.x, p.y);
            if (occ != null) {
                p.owner = occ.owner.getIndex();
            }
        }

        // 2. Minions attack nearby enemies or opposing Overlord Sanctum
        for (MinionSlot m : minionPool) {
            if (!m.active) continue;
            Player opp = gameManager.getPlayer(1 - m.owner.getIndex());

            int distToSanctum = Math.abs(m.x - opp.getSanctumX()) + Math.abs(m.y - opp.getSanctumY());
            if (distToSanctum <= m.type.range) {
                opp.setSanctumHp(opp.getSanctumHp() - m.type.atk);
                m.owner.setScore(m.owner.getScore() + m.type.atk * 2);
                continue;
            }

            MinionSlot bestTarget = null;
            for (MinionSlot e : minionPool) {
                if (e.active && e.owner != m.owner) {
                    int d = Math.abs(m.x - e.x) + Math.abs(m.y - e.y);
                    if (d <= m.type.range && (bestTarget == null || e.hp < bestTarget.hp)) {
                        bestTarget = e;
                    }
                }
            }
            if (bestTarget != null) {
                bestTarget.hp -= m.type.atk;
            }
        }

        // 3. Remove slain minions
        for (MinionSlot m : minionPool) {
            if (m.active && m.hp <= 0) {
                m.active = false;
                m.rootGroup.setVisible(false);
                Player killer = gameManager.getPlayer(1 - m.owner.getIndex());
                killer.setScore(killer.getScore() + 5);
            }
        }

        // 4. Award Pentagram control score
        for (Player p : gameManager.getActivePlayers()) {
            int pIdx = p.getIndex();
            int owned = 0;
            for (Pentagram pent : pentagrams) {
                if (pent.owner == pIdx) owned++;
            }
            p.setScore(p.getScore() + owned * 2);
        }
    }

    private void refreshGraphics() {
        for (Player p : gameManager.getPlayers()) {
            p.getScoreText().setText("SCORE: " + p.getScore());
            p.getStatusText().setText("SANCTUM: " + p.getSanctumHp() + " HP");
        }
        for (Pentagram p : pentagrams) {
            int col = (p.owner == -1) ? 0x9f7aea : gameManager.getPlayer(p.owner).getColorToken();
            p.auraCircle.setFillColor(col).setLineColor(col);
            p.debugHex.setFillColor(col);
            tooltipModule.setTooltipText(p.group,
                String.format("Pentagram %d (%d, %d)\nOwner: %s",
                    p.id, p.x, p.y, p.owner == -1 ? "Neutral" : "Player " + p.owner));
        }
        for (MinionSlot m : minionPool) {
            if (m.active) {
                int barW = Math.max(4, (int) Math.round(44.0 * m.hp / m.type.maxHp));
                m.hpBar.setWidth(barW);
                m.debugText.setText(m.type.code + m.id);
                tooltipModule.setTooltipText(m.rootGroup,
                    String.format("Minion %d (%s - Player %d)\nPos: (%d, %d) | HP: %d/%d",
                        m.id, m.type.name(), m.owner.getIndex(), m.x, m.y, m.hp, m.type.maxHp));
            }
        }
    }

    private void checkEndConditions() {
        Player p0 = gameManager.getPlayer(0);
        Player p1 = gameManager.getPlayer(1);
        if (p0.getSanctumHp() <= 0 || p1.getSanctumHp() <= 0) {
            if (p0.getSanctumHp() > 0) p0.setScore(p0.getScore() + 50);
            if (p1.getSanctumHp() > 0) p1.setScore(p1.getScore() + 50);
            refreshGraphics();
            gameManager.endGame();
        }
    }

    @Override
    public void onEnd() {
        int[] scores = gameManager.getPlayers().stream().mapToInt(Player::getScore).toArray();
        String[] texts = gameManager.getPlayers().stream()
            .map(p -> p.getScore() + " pts (Sanctum: " + p.getSanctumHp() + " HP)")
            .toArray(String[]::new);
        endScreenModule.setScores(scores, texts);
    }
}

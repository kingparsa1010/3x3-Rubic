import org.kociemba.twophase.Search;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Rubik's Cube 3x3 Solver GUI (Swing) using Herbert Kociemba's Two-Phase Java package (twophase.jar).
 *
 * Features:
 * 1) Scramble Mode: user enters moves like "R U R' U' F2"
 * 2) Manual Color Input: user clicks stickers to set colors; centers are fixed
 *
 * Output:
 * - Uses Search.solution(facelets, maxDepth, timeOutSeconds, useSeparator)
 *
 * Facelet format required by Kociemba:
 * "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB" (URFDLB order)
 */
public class RubikSolverGUI extends JFrame {

    // ------------------------
    // UI color palette (letters are shown on stickers to reduce ambiguity)
    // ------------------------
    private static final char[] COLOR_KEYS = {'W', 'R', 'G', 'Y', 'O', 'B'};
    private static final Map<Character, Color> COLOR_HEX = Map.of(
            'W', new Color(0xF5F5F5),
            'R', new Color(0xE53935),
            'G', new Color(0x43A047),
            'Y', new Color(0xFDD835),
            'O', new Color(0xFB8C00),
            'B', new Color(0x1E88E5)
    );

    // Faces in URFDLB convention
    private static final char[] FACES = {'U', 'R', 'F', 'D', 'L', 'B'};

    // Default center colors (common physical cube convention)
    private static final Map<Character, Character> DEFAULT_FACE_CENTER_COLOR = Map.of(
            'U', 'W', // Up    -> White
            'R', 'R', // Right -> Red
            'F', 'G', // Front -> Green
            'D', 'Y', // Down  -> Yellow
            'L', 'O', // Left  -> Orange
            'B', 'B'  // Back  -> Blue
    );

    // Solved facelets (URFDLB)
    private static final String SOLVED =
            "UUUUUUUUU" +
            "RRRRRRRRR" +
            "FFFFFFFFF" +
            "DDDDDDDDD" +
            "LLLLLLLLL" +
            "BBBBBBBBB";

    // Face indices in the 54-char facelet string (URFDLB order, each face 0..8 row-major)
    private static final int[] IDX_U = range(0, 9);
    private static final int[] IDX_R = range(9, 18);
    private static final int[] IDX_F = range(18, 27);
    private static final int[] IDX_D = range(27, 36);
    private static final int[] IDX_L = range(36, 45);
    private static final int[] IDX_B = range(45, 54);

    private static final Map<Character, int[]> FACE_TO_IDXS = Map.of(
            'U', IDX_U, 'R', IDX_R, 'F', IDX_F, 'D', IDX_D, 'L', IDX_L, 'B', IDX_B
    );

    // ------------------------
    // Swing components
    // ------------------------
    private final JTextArea scrambleInput = new JTextArea(3, 40);
    private final JTextArea scrambleOutput = new JTextArea(6, 40);

    private final JTextArea manualOutput = new JTextArea(6, 40);
    private final Map<Character, JButton[]> manualButtons = new HashMap<>();
    private final Map<Character, char[]> manualColors = new HashMap<>();

    public RubikSolverGUI() {
        super("Rubik's Cube 3x3 Solver (Java Swing + Kociemba Two-Phase)");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Scramble Mode", buildScrambleTab());
        tabs.addTab("Manual Color Input", buildManualTab());

        setContentPane(tabs);
        resetManual();
    }

    // ------------------------
    // Tab 1: Scramble Mode
    // ------------------------
    private JPanel buildScrambleTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel info = new JLabel("<html>"
                + "<b>Enter scramble moves</b> (example: <code>R U R' U' F2</code>) and click Solve.<br>"
                + "This app will apply the scramble to a solved cube, then solve it using Kociemba two-phase."
                + "</html>");
        panel.add(info, BorderLayout.NORTH);

        scrambleInput.setFont(new Font("Consolas", Font.PLAIN, 16));
        JScrollPane inputScroll = new JScrollPane(scrambleInput);
        inputScroll.setBorder(new TitledBorder("Scramble"));

        JButton solveBtn = new JButton("Solve");
        solveBtn.addActionListener(e -> onSolveScramble());

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> scrambleInput.setText(""));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(solveBtn);
        buttons.add(clearBtn);

        scrambleOutput.setFont(new Font("Consolas", Font.PLAIN, 16));
        scrambleOutput.setEditable(false);
        JScrollPane outScroll = new JScrollPane(scrambleOutput);
        outScroll.setBorder(new TitledBorder("Solution"));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.add(inputScroll, BorderLayout.NORTH);
        center.add(buttons, BorderLayout.CENTER);
        center.add(outScroll, BorderLayout.SOUTH);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private void onSolveScramble() {
        String scramble = normalizeScramble(scrambleInput.getText());
        if (scramble.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter a scramble.", "Input required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            char[] state = SOLVED.toCharArray();
            applyScrambleToFacelets(state, scramble);

            String facelets = new String(state);

            // Kociemba solver call:
            // maxDepth: typical 21 or 22
            // timeOut: seconds (often used like 5..20)
            // useSeparator: true -> spaces between moves
            String sol = Search.solution(facelets, 21, 10, true);

            // The library returns error messages as strings starting with "Error"
            if (sol != null && sol.startsWith("Error")) {
                throw new RuntimeException(sol);
            }

            scrambleOutput.setText(sol + "\n\nFacelets:\n" + facelets);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to solve.\n\nDetails:\n" + ex.getMessage(),
                    "Solver error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------
    // Tab 2: Manual Color Input
    // ------------------------
    private JPanel buildManualTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel info = new JLabel("<html>"
                + "<b>Manual input:</b> Click stickers to cycle colors. Centers are fixed.<br>"
                + "When all faces are filled correctly, click Solve."
                + "</html>");
        panel.add(info, BorderLayout.NORTH);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> resetManual());
        JButton solveBtn = new JButton("Solve");
        solveBtn.addActionListener(e -> onSolveManual());
        topButtons.add(resetBtn);
        topButtons.add(solveBtn);

        JPanel facesPanel = new JPanel(new GridLayout(2, 3, 10, 10));

        // Layout: [U R F] / [D L B]
        char[][] layout = {
                {'U', 'R', 'F'},
                {'D', 'L', 'B'}
        };

        for (char[] row : layout) {
            for (char face : row) {
                facesPanel.add(buildFaceGrid(face));
            }
        }

        manualOutput.setFont(new Font("Consolas", Font.PLAIN, 16));
        manualOutput.setEditable(false);
        JScrollPane outScroll = new JScrollPane(manualOutput);
        outScroll.setBorder(new TitledBorder("Solution"));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.add(topButtons, BorderLayout.NORTH);
        center.add(facesPanel, BorderLayout.CENTER);
        center.add(outScroll, BorderLayout.SOUTH);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFaceGrid(char face) {
        JPanel frame = new JPanel(new GridLayout(3, 3, 4, 4));
        frame.setBorder(new TitledBorder("Face " + face));

        JButton[] btns = new JButton[9];
        char[] colors = new char[9];
        manualButtons.put(face, btns);
        manualColors.put(face, colors);

        for (int i = 0; i < 9; i++) {
            JButton b = new JButton();
            b.setFont(new Font("Consolas", Font.BOLD, 14));
            final int idx = i;

            // Center sticker is locked
            if (idx == 4) {
                b.setEnabled(false);
            } else {
                b.addActionListener(e -> {
                    cycleColor(face, idx);
                    refreshSticker(face, idx);
                });
            }

            btns[i] = b;
            frame.add(b);
        }
        return frame;
    }

    private void resetManual() {
        // Fill each face with its default center color (looks solved initially)
        for (char face : FACES) {
            char centerColor = DEFAULT_FACE_CENTER_COLOR.get(face);
            char[] arr = manualColors.get(face);
            Arrays.fill(arr, centerColor);
            arr[4] = centerColor;
        }
        refreshAllManual();
        manualOutput.setText("");
    }

    private void cycleColor(char face, int idx) {
        char cur = manualColors.get(face)[idx];
        int pos = indexOf(COLOR_KEYS, cur);
        int next = (pos + 1) % COLOR_KEYS.length;
        manualColors.get(face)[idx] = COLOR_KEYS[next];
    }

    private void refreshSticker(char face, int idx) {
        char c = manualColors.get(face)[idx];
        JButton b = manualButtons.get(face)[idx];
        b.setBackground(COLOR_HEX.get(c));
        b.setText(String.valueOf(c));
    }

    private void refreshAllManual() {
        for (char face : FACES) {
            for (int i = 0; i < 9; i++) {
                refreshSticker(face, i);
            }
        }
    }

    private void onSolveManual() {
        try {
            // Validate: each color appears exactly 9 times
            Map<Character, Integer> counts = new HashMap<>();
            for (char c : COLOR_KEYS) counts.put(c, 0);

            for (char face : FACES) {
                for (char c : manualColors.get(face)) {
                    counts.put(c, counts.getOrDefault(c, 0) + 1);
                }
            }

            List<String> bad = new ArrayList<>();
            for (char c : COLOR_KEYS) {
                if (!counts.get(c).equals(9)) {
                    bad.add(c + "=" + counts.get(c));
                }
            }
            if (!bad.isEmpty()) {
                throw new IllegalArgumentException("Each color must appear exactly 9 times: " + String.join(", ", bad));
            }

            // Map colors -> face letters based on centers (user-friendly)
            Map<Character, Character> colorToFace = new HashMap<>();
            for (char face : FACES) {
                char centerColor = manualColors.get(face)[4];
                if (colorToFace.containsKey(centerColor)) {
                    throw new IllegalArgumentException("Two faces have the same center color: " + centerColor);
                }
                colorToFace.put(centerColor, face);
            }

            // Build facelets in URFDLB order (Kociemba requirement)
            char[] facelets = new char[54];
            Arrays.fill(facelets, '?');

            for (char face : FACES) {
                int[] idxs = FACE_TO_IDXS.get(face);
                char[] cols = manualColors.get(face);

                for (int i = 0; i < 9; i++) {
                    char col = cols[i];
                    Character faceLetter = colorToFace.get(col);
                    if (faceLetter == null) {
                        throw new IllegalArgumentException("Unmapped color: " + col);
                    }
                    facelets[idxs[i]] = faceLetter;
                }
            }

            String faceletStr = new String(facelets);

            // Solve
            String sol = Search.solution(faceletStr, 21, 10, true);
            if (sol != null && sol.startsWith("Error")) {
                throw new RuntimeException(sol);
            }

            manualOutput.setText(sol + "\n\nFacelets:\n" + faceletStr);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid cube state or input.\n\nDetails:\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------
    // Scramble -> facelet moves (apply U D R L F B, with 2 and ' modifiers)
    // This keeps things user-friendly: user enters scramble, program computes facelets.
    // ------------------------
    private static void applyScrambleToFacelets(char[] state, String scramble) {
        String[] tokens = scramble.trim().split("\\s+");
        for (String t : tokens) {
            applyMoveToken(state, t);
        }
    }

    private static void applyMoveToken(char[] s, String token) {
        token = token.trim();
        if (token.isEmpty()) return;

        char face = token.charAt(0);
        int times = 1;

        if (token.length() == 2) {
            char suf = token.charAt(1);
            if (suf == '2') times = 2;
            else if (suf == '\'') times = 3;
            else throw new IllegalArgumentException("Invalid move suffix: " + token);
        } else if (token.length() > 2) {
            throw new IllegalArgumentException("Invalid move token: " + token);
        }

        for (int i = 0; i < times; i++) {
            switch (face) {
                case 'U' -> moveU(s);
                case 'D' -> moveD(s);
                case 'R' -> moveR(s);
                case 'L' -> moveL(s);
                case 'F' -> moveF(s);
                case 'B' -> moveB(s);
                default -> throw new IllegalArgumentException("Invalid move: " + token);
            }
        }
    }

    // Rotate a 3x3 face clockwise (indices within the 54-facelet array)
    private static void rotateFaceCW(char[] st, int[] face) {
        // 0 1 2
        // 3 4 5
        // 6 7 8
        int a0 = face[0], a1 = face[1], a2 = face[2], a3 = face[3], a5 = face[5], a6 = face[6], a7 = face[7], a8 = face[8];
        char t0 = st[a0], t1 = st[a1], t2 = st[a2], t3 = st[a3], t5 = st[a5], t6 = st[a6], t7 = st[a7], t8 = st[a8];

        st[a0] = t6; st[a6] = t8; st[a8] = t2; st[a2] = t0;
        st[a1] = t3; st[a3] = t7; st[a7] = t5; st[a5] = t1;
    }

    private static void moveU(char[] st) {
        rotateFaceCW(st, IDX_U);
        int[] f = {18, 19, 20};
        int[] r = {9, 10, 11};
        int[] b = {45, 46, 47};
        int[] l = {36, 37, 38};
        char[] tmpF = pick(st, f);
        char[] tmpL = pick(st, l);
        char[] tmpB = pick(st, b);
        char[] tmpR = pick(st, r);

        put(st, f, tmpL);
        put(st, l, tmpB);
        put(st, b, tmpR);
        put(st, r, tmpF);
    }

    private static void moveD(char[] st) {
        rotateFaceCW(st, IDX_D);
        int[] f = {24, 25, 26};
        int[] r = {15, 16, 17};
        int[] b = {51, 52, 53};
        int[] l = {42, 43, 44};
        char[] tmpF = pick(st, f);
        char[] tmpL = pick(st, l);
        char[] tmpB = pick(st, b);
        char[] tmpR = pick(st, r);

        // D clockwise cycles F -> L -> B -> R -> F
        put(st, l, tmpF);
        put(st, b, tmpL);
        put(st, r, tmpB);
        put(st, f, tmpR);
    }

    private static void moveR(char[] st) {
        rotateFaceCW(st, IDX_R);
        int[] u = {2, 5, 8};
        int[] f = {20, 23, 26};
        int[] d = {29, 32, 35};
        int[] b = {45, 48, 51}; // B left column
        char[] tmpU = pick(st, u);
        char[] tmpF = pick(st, f);
        char[] tmpD = pick(st, d);
        char[] tmpB = pick(st, b);

        // U -> F
        put(st, f, tmpU);
        // F -> D
        put(st, d, tmpF);
        // D -> B (reversed)
        put(st, b, reverse(tmpD));
        // B -> U (reversed)
        put(st, u, reverse(tmpB));
    }

    private static void moveL(char[] st) {
        rotateFaceCW(st, IDX_L);
        int[] u = {0, 3, 6};
        int[] f = {18, 21, 24};
        int[] d = {27, 30, 33};
        int[] b = {47, 50, 53}; // B right column
        char[] tmpU = pick(st, u);
        char[] tmpF = pick(st, f);
        char[] tmpD = pick(st, d);
        char[] tmpB = pick(st, b);

        // U -> B (reversed)
        put(st, b, reverse(tmpU));
        // B -> D (reversed)
        put(st, d, reverse(tmpB));
        // D -> F
        put(st, f, tmpD);
        // F -> U
        put(st, u, tmpF);
    }

    private static void moveF(char[] st) {
        rotateFaceCW(st, IDX_F);
        int[] u = {6, 7, 8};      // U bottom row
        int[] r = {9, 12, 15};    // R left column
        int[] d = {27, 28, 29};   // D top row
        int[] l = {38, 41, 44};   // L right column
        char[] tmpU = pick(st, u);
        char[] tmpR = pick(st, r);
        char[] tmpD = pick(st, d);
        char[] tmpL = pick(st, l);

        // U -> R
        put(st, r, tmpU);
        // R -> D (reversed)
        put(st, d, reverse(tmpR));
        // D -> L (reversed)
        put(st, l, reverse(tmpD));
        // L -> U
        put(st, u, tmpL);
    }

    private static void moveB(char[] st) {
        rotateFaceCW(st, IDX_B);
        int[] u = {0, 1, 2};      // U top row
        int[] r = {11, 14, 17};   // R right column
        int[] d = {33, 34, 35};   // D bottom row
        int[] l = {36, 39, 42};   // L left column
        char[] tmpU = pick(st, u);
        char[] tmpR = pick(st, r);
        char[] tmpD = pick(st, d);
        char[] tmpL = pick(st, l);

        // U -> R
        put(st, r, tmpU);
        // R -> D (reversed)
        put(st, d, reverse(tmpR));
        // D -> L
        put(st, l, tmpD);
        // L -> U (reversed)
        put(st, u, reverse(tmpL));
    }

    // ------------------------
    // Helpers
    // ------------------------
    private static String normalizeScramble(String s) {
        return Arrays.stream(s.replace("\n", " ").trim().split("\\s+"))
                .filter(t -> !t.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private static char[] pick(char[] st, int[] idx) {
        char[] out = new char[idx.length];
        for (int i = 0; i < idx.length; i++) out[i] = st[idx[i]];
        return out;
    }

    private static void put(char[] st, int[] idx, char[] vals) {
        for (int i = 0; i < idx.length; i++) st[idx[i]] = vals[i];
    }

    private static char[] reverse(char[] a) {
        char[] b = new char[a.length];
        for (int i = 0; i < a.length; i++) b[i] = a[a.length - 1 - i];
        return b;
    }

    private static int indexOf(char[] arr, char x) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == x) return i;
        return -1;
    }

    private static int[] range(int startInclusive, int endExclusive) {
        int[] r = new int[endExclusive - startInclusive];
        for (int i = 0; i < r.length; i++) r[i] = startInclusive + i;
        return r;
    }

    // ------------------------
    // Main
    // ------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RubikSolverGUI().setVisible(true));
    }
}
import java.util.*;

class Player {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int width = in.nextInt();
        int height = in.nextInt();
        int leagueLevel = in.nextInt();
        int mySanctumX = in.nextInt(), mySanctumY = in.nextInt();
        int oppSanctumX = in.nextInt(), oppSanctumY = in.nextInt();

        while (true) {
            int gems = in.nextInt();
            int myHp = in.nextInt();
            int oppHp = in.nextInt();
            for (int y = 0; y < height; y++) in.next();

            int pCount = in.nextInt();
            List<int[]> pentagrams = new ArrayList<>();
            for (int i = 0; i < pCount; i++) {
                pentagrams.add(new int[]{in.nextInt(), in.nextInt(), in.nextInt(), in.nextInt()});
            }

            int mCount = in.nextInt();
            List<String[]> myMinions = new ArrayList<>();
            for (int i = 0; i < mCount; i++) {
                String[] m = {in.next(), in.next(), in.next(), in.next(), in.next(), in.next()};
                if ("0".equals(m[1])) myMinions.add(m);
            }

            List<String> cmds = new ArrayList<>();
            for (int[] p : pentagrams) {
                if (p[3] == 0 && gems >= 3) {
                    cmds.add("SUMMON " + p[0] + " GUARD");
                    gems -= 3;
                    break;
                }
            }
            for (String[] m : myMinions) {
                if (gems < 1) break;
                cmds.add("ACTIVATE " + m[0] + " " + (width / 2) + " " + (height / 2));
                gems -= 1;
            }
            System.out.println(cmds.isEmpty() ? "WAIT" : String.join(" ; ", cmds));
        }
    }
}

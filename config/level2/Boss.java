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

            // 1. Activate existing minions first to clear home pentagram & advance!
            for (int i = 0; i < myMinions.size(); i++) {
                if (gems < 1) break;
                String[] m = myMinions.get(i);
                int tx = oppSanctumX, ty = oppSanctumY;
                if (leagueLevel >= 2 && i == 0) {
                    for (int[] p : pentagrams) {
                        if (p[3] != 0) {
                            tx = p[1];
                            ty = p[2];
                            break;
                        }
                    }
                }
                cmds.add("ACTIVATE " + m[0] + " " + tx + " " + ty);
                gems -= 1;
            }

            // 2. Summon on closest-to-enemy controlled pentagram
            pentagrams.sort(Comparator.comparingInt(p -> Math.abs(p[1] - oppSanctumX) + Math.abs(p[2] - oppSanctumY)));
            for (int[] p : pentagrams) {
                if (p[3] != 0) continue;
                if (leagueLevel >= 3 && gems >= 4) {
                    cmds.add("SUMMON " + p[0] + " WRAITH");
                    gems -= 4;
                } else if (leagueLevel >= 2 && gems >= 3) {
                    cmds.add("SUMMON " + p[0] + " GUARD");
                    gems -= 3;
                } else if (gems >= 2) {
                    cmds.add("SUMMON " + p[0] + " HOUND");
                    gems -= 2;
                }
            }

            // 3. Frenzy double-activation in League 3
            if (leagueLevel >= 3) {
                for (String[] m : myMinions) {
                    if (gems < 2) break;
                    cmds.add("ACTIVATE " + m[0] + " " + oppSanctumX + " " + oppSanctumY);
                    gems -= 2;
                }
            }

            System.out.println(cmds.isEmpty() ? "WAIT" : String.join(" ; ", cmds));
        }
    }
}

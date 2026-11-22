import java.util.*;

/**
 * Knapsack Gem-Allocator & Forward Pentagram Summoner AI for Overlord's Labyrinth:
 * - Allocates Energy Gems optimally between activating existing minions toward enemy Pentagrams/Sanctum
 *   and summoning new WRAITH / HOUND / GUARD minions on the most forward controlled Pentagram!
 * - In League 3, uses double-activation (Frenzy) on frontline hounds/wraiths to blitz the enemy Sanctum!
 */
public class Agent1 {
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

            // 1. Activate existing minions first (1 Gem each)
            for (int i = 0; i < myMinions.size(); i++) {
                if (gems < 1) break;
                String[] m = myMinions.get(i);
                int mid = Integer.parseInt(m[0]);
                int tx = oppSanctumX, ty = oppSanctumY;
                // First minion captures neutral pentagrams along the way
                if (i == 0) {
                    for (int[] p : pentagrams) {
                        if (p[3] != 0) {
                            tx = p[1];
                            ty = p[2];
                            break;
                        }
                    }
                }
                cmds.add("ACTIVATE " + mid + " " + tx + " " + ty);
                gems -= 1;
            }

            // 2. Summon on most forward owned Pentagram
            pentagrams.sort((a, b) -> Integer.compare(
                Math.abs(a[1] - oppSanctumX) + Math.abs(a[2] - oppSanctumY),
                Math.abs(b[1] - oppSanctumX) + Math.abs(b[2] - oppSanctumY)
            ));

            for (int[] p : pentagrams) {
                if (p[3] != 0) continue;
                if (leagueLevel >= 2 && gems >= 4) {
                    cmds.add("SUMMON " + p[0] + " WRAITH");
                    gems -= 4;
                } else if (gems >= 3) {
                    cmds.add("SUMMON " + p[0] + " GUARD");
                    gems -= 3;
                } else if (gems >= 2) {
                    cmds.add("SUMMON " + p[0] + " HOUND");
                    gems -= 2;
                }
            }

            // 3. League 3 Frenzy: spend remaining gems (2 Gems) on a 2nd activation for frontline minions!
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

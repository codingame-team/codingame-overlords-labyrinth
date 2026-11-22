package com.codingame.game;

import com.codingame.gameengine.core.AbstractMultiplayerPlayer;
import com.codingame.gameengine.module.entities.Text;

public class Player extends AbstractMultiplayerPlayer {
    private int sanctumX;
    private int sanctumY;
    private int sanctumHp = 40;
    private int gems;
    private Text scoreText;
    private Text statusText;

    @Override
    public int getExpectedOutputLines() {
        return 1;
    }

    public int getSanctumX() {
        return sanctumX;
    }

    public int getSanctumY() {
        return sanctumY;
    }

    public void setSanctumPos(int x, int y) {
        this.sanctumX = x;
        this.sanctumY = y;
    }

    public int getSanctumHp() {
        return sanctumHp;
    }

    public void setSanctumHp(int sanctumHp) {
        this.sanctumHp = Math.max(0, sanctumHp);
    }

    public int getGems() {
        return gems;
    }

    public void setGems(int gems) {
        this.gems = Math.max(0, gems);
    }

    public Text getScoreText() {
        return scoreText;
    }

    public void setScoreText(Text scoreText) {
        this.scoreText = scoreText;
    }

    public Text getStatusText() {
        return statusText;
    }

    public void setStatusText(Text statusText) {
        this.statusText = statusText;
    }
}

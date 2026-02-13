package com.siickzz.ktsacademy.quests;

public final class QuestProgress {
	public int progress;
	public boolean claimed;
	public int tier;

	public QuestProgress() {
	}

	public QuestProgress(int progress, boolean claimed) {
		this.progress = Math.max(0, progress);
		this.claimed = claimed;
		this.tier = 0;
	}
}

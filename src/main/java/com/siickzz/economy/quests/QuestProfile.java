package com.siickzz.economy.quests;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class QuestProfile {
	public Map<String, QuestProgress> quests = new HashMap<>();
	public Set<String> capturedSpecies = new HashSet<>();

	public QuestProfile() {
	}
}

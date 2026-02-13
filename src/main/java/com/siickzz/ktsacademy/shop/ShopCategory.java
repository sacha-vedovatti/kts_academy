package com.siickzz.ktsacademy.shop;

public enum ShopCategory {
	BALLS("Balls"),
	BAGS("Bags"),
	STONES("Stones");

	private final String displayName;

	ShopCategory(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}

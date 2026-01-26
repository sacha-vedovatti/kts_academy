/*
** EPITECH PROJECT, 2026
** Economy
** File description:
** PlayerAcount class
*/

package com.siickzz.economy.economy;

public class PlayerAccount {
    private double balance;
    private int pokedexRewardTier;

    public PlayerAccount() {
    }

    public PlayerAccount(double balance) {
        this.balance = Math.max(0.0, balance);
    }

    public void add(double amount) {
        if (amount <= 0) {
            return;
        }
        balance += amount;
    }

    public boolean remove(double amount) {
        if (amount <= 0) {
            return true;
        }
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = Math.max(0.0, balance);
    }

    public int getPokedexRewardTier() {
        return pokedexRewardTier;
    }

    public void setPokedexRewardTier(int pokedexRewardTier) {
        this.pokedexRewardTier = Math.max(0, pokedexRewardTier);
    }
}

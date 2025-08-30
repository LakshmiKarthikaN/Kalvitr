package com.kalvitrack.kalvitrack_backend;

public class Main {
    public static void main(String[] args) {
        LombokTest t = new LombokTest("Karthika", 25);
        System.out.println(t.getName()); // Should compile without errors
    }
}

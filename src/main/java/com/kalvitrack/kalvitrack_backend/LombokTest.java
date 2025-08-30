package com.kalvitrack.kalvitrack_backend;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LombokTest {
    @Getter
    private final String name;

    @Getter
    private final int age;
}

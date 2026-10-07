package com.gp.slotsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SlotSyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(SlotSyncApplication.class, args);
    }
}

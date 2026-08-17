package com.hujiayucc.notask;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ModuleConfigTest {
    @Test
    public void modeKeyUsesPackageNamespace() {
        assertEquals("hide_mode.com.example.app", ModuleConfig.modeKey("com.example.app"));
    }

    @Test
    public void normalizeModeFallsBackToBack() {
        assertEquals(ModuleConfig.MODE_BACK, ModuleConfig.normalizeMode(-1));
        assertEquals(ModuleConfig.MODE_BACK, ModuleConfig.normalizeMode(ModuleConfig.MODE_BACK));
        assertEquals(ModuleConfig.MODE_BACKGROUND, ModuleConfig.normalizeMode(ModuleConfig.MODE_BACKGROUND));
    }
}
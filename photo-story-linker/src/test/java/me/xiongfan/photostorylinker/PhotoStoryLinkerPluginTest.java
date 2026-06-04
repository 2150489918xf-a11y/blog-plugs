package me.xiongfan.photostorylinker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoStoryLinkerPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SchemeManager schemeManager;

    @Mock
    Scheme scheme;

    @InjectMocks
    PhotoStoryLinkerPlugin plugin;

    @Test
    void contextLoads() {
        when(schemeManager.get(PhotoStoryBinding.class)).thenReturn(scheme);

        plugin.start();
        plugin.stop();

        verify(schemeManager).register(eq(PhotoStoryBinding.class));
        verify(schemeManager).unregister(eq(scheme));
    }
}

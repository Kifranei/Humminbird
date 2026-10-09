package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSearchTargetTest {
    @Test fun onlineSearchTargetsStayOnTheLocalLibrary() {
        for (library in listOf("local", "netease", "navidrome", "opensubsonic", "emby", "webdav")) {
            for (preference in listOf("local", "lx", "musicfree", "netease", "unknown", "")) {
                assertEquals("local", resolveHomeSearchTarget(library, preference))
            }
        }
    }
}

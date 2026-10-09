package com.ella.music.data.netease

import org.junit.Assert.*
import org.junit.Test

class NeteaseLinksTest {
    @Test fun artistWikiPresets() {
        assertEquals("orpheus://rnpage?component=music-reactnative-artistwiki&artistId=123", NeteaseLinks.build(NeteaseLinkSettings(NeteaseLinkTarget.App), NeteaseLinkKind.ArtistWiki, "123"))
    }
    @Test fun customIntentTemplateKeepsFragmentAndSubstitutesId() {
        val template = "intent://artist/{id}#Intent;scheme=orpheus;package=com.netease.cloudmusic;end"
        assertEquals(template.replace("{id}", "123"), NeteaseLinks.build(NeteaseLinkSettings(NeteaseLinkTarget.Custom, custom = mapOf(NeteaseLinkKind.Artist to template)), NeteaseLinkKind.Artist, "123"))
    }
}

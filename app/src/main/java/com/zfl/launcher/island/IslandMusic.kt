package com.zfl.launcher.island

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zfl.launcher.R
import com.zfl.launcher.ui.theme.IslandTokens
import com.zfl.launcher.ui.theme.Tokens

/** Artwork on the left of the camera, the live waveform on the right, like iOS. */
@Composable
internal fun CompactMusic(media: IslandSession, artwork: ImageBitmap?, accent: Color?) {
    Row(
        Modifier.width(LocalIslandStyle.current.compactWidth.dp).height(IslandTokens.CompactHeight).padding(horizontal = IslandTokens.CompactInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IslandArtwork(artwork, IslandTokens.CompactArtwork, IslandTokens.CompactArtworkRadius)
        Spacer(Modifier.weight(1f))
        IslandWaveform(media.playback == IslandPlayback.Playing, accent ?: IslandTokens.Waveform,
            IslandTokens.Wave, IslandTokens.WaveHeight, IslandTokens.WaveBar, Modifier.padding(end = IslandTokens.CompactInset))
    }
}

/** The expanded player: artwork, title and artist, the scrubber and transport controls. */
@Composable
internal fun MusicCard(
    media: IslandSession, artwork: ImageBitmap?, accent: Color?, width: Dp,
    command: (IslandCommand, Long?) -> Unit, openPlayer: () -> Unit,
) {
    Column(
        Modifier.width(width).padding(start = IslandTokens.CardPadding, end = IslandTokens.CardPadding,
            top = IslandTokens.CardTop, bottom = IslandTokens.CardBottom),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).then(if (media.canOpen) Modifier.clickable(
                    interactionSource = null, indication = null, role = Role.Button,
                    onClickLabel = stringResource(R.string.island_open_player), onClick = openPlayer,
                ) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IslandTokens.Gap),
            ) {
                IslandArtwork(artwork, IslandTokens.Artwork, IslandTokens.ArtworkRadius)
                Column(Modifier.weight(1f)) {
                    Text(media.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.island_music), color = Tokens.TextPrimary,
                        fontSize = IslandTokens.Title, lineHeight = IslandTokens.TitleLine, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    media.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                        Text(artist, color = IslandTokens.TextSecondary, fontSize = IslandTokens.Subtitle,
                            lineHeight = IslandTokens.SubtitleLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(IslandTokens.Gap))
            IslandWaveform(media.playback == IslandPlayback.Playing, accent ?: IslandTokens.Waveform,
                IslandTokens.CardWave, IslandTokens.CardWaveHeight, IslandTokens.CardWaveBar)
        }
        if (media.hasProgress) {
            Spacer(Modifier.height(IslandTokens.Gap))
            IslandProgress(media, { command(IslandCommand.Seek, it) })
        } else Spacer(Modifier.height(IslandTokens.Gap))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(IslandTokens.ControlGap), verticalAlignment = Alignment.CenterVertically) {
                IslandIconButton(Icons.Rounded.FastRewind, R.string.island_previous, IslandCommand.Previous in media.commands,
                    IslandTokens.ControlGlyph, { command(IslandCommand.Previous, null) })
                val playing = media.playback != IslandPlayback.Paused
                val action = if (playing) IslandCommand.Pause else IslandCommand.Play
                IslandIconButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) R.string.island_pause else R.string.island_play, action in media.commands,
                    IslandTokens.PlayGlyph, { command(action, null) }, size = IslandTokens.Control)
                IslandIconButton(Icons.Rounded.FastForward, R.string.island_next, IslandCommand.Next in media.commands,
                    IslandTokens.ControlGlyph, { command(IslandCommand.Next, null) })
            }
        }
    }
}

/** Empty compact slot for a session that ended while its content morphs away. */
@Composable
internal fun CompactPlaceholder() {
    Box(Modifier.size(LocalIslandStyle.current.compactWidth.dp, IslandTokens.CompactHeight))
}

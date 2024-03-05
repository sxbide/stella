/*
 *     Stella - A tablist API
 *     Copyright (C) 2024  ConnorChickenway
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package xyz.connorchickenway.stella.legacy.tab;

import net.minecraft.util.com.mojang.authlib.GameProfile;
import net.minecraft.util.com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import xyz.connorchickenway.stella.legacy.util.LegacyUtils;
import xyz.connorchickenway.stella.legacy.wrapper.PacketPlayerInfoWrapper;
import xyz.connorchickenway.stella.legacy.wrapper.PacketScoreboardTeamWrapper;
import xyz.connorchickenway.stella.tab.PlayerTab;
import xyz.connorchickenway.stella.tab.entry.TabEntry;
import xyz.connorchickenway.stella.tab.modifier.TabModifier;
import xyz.connorchickenway.stella.tab.modifier.TabUpdate;
import xyz.connorchickenway.stella.tab.skin.Skin;
import xyz.connorchickenway.stella.util.ServerVersion;

import static xyz.connorchickenway.stella.legacy.util.LegacyUtils.is1_7;

public class LegacyTab extends PlayerTab {


    public LegacyTab(Player player) {
        super(player, new TabEntry[is1_7(player) ? WIDTH - 1 : WIDTH][HEIGHT]);
    }

    @Override
    public void init(TabModifier tabModifier) {
        final boolean isLegacy = is1_7(player);
        final int divisor = isLegacy ? 3 : 20;
        if (isLegacy) {
            for (Player online : Bukkit.getOnlinePlayers())
                PacketPlayerInfoWrapper.removePlayerInfo(player, online) ;
        }
        for(int index = 0; index <
                (isLegacy ? 60 : 80); index++) {
            // if player is offline then break
            if (!player.isOnline()) break;
            int x = isLegacy ? index % divisor : index / divisor;
            int y = isLegacy ? index / divisor : index % divisor;
            TabEntry tabEntry = entries[x][y] = buildEntry(x, y, tabModifier).initName();
            GameProfile gameProfile = createProfile(tabEntry);
            String text = tabEntry.getText();
            PacketPlayerInfoWrapper info = new PacketPlayerInfoWrapper(PacketPlayerInfoWrapper.
                    PlayerInfoAction.ADD_PLAYER);
            //if player has 1.7 version then send PacketPlayOutScoreboardTeam
            if (isLegacy) {
                text = LegacyUtils.prefixAndSuffix(text);
                String name = NAMES[x][y];
                PacketScoreboardTeamWrapper team = new PacketScoreboardTeamWrapper(true, name);
                team.addEntry(name);
                if (!text.isEmpty()) {
                    String[] split = text.split(":;:");
                    if (split.length > 1) {
                        team.setSuffix(split[1]);
                    }
                    team.setPrefix(split[0]);
                }
                team.send(player);
                info.setText(name);
            }
            else info.setText(text);
            Skin skin = tabEntry.hasSkin() ? tabEntry.getSkin() : Skin.DEFAULT;
            gameProfile.getProperties().put("textures",
                    new Property("textures", skin.getValue(), skin.getSignature()));
            info.setPing(tabEntry.getPing());
            info.setProfile(gameProfile);
            info.send(player);
        }
        this.creating.set(false);
    }

    @Override
    public void update(TabUpdate tabUpdate) {
        final boolean isLegacy = is1_7(player);
        for(TabEntry updateEntry : tabUpdate.update(player)) {
            if (!player.isOnline()) break;
            if (isLegacy && updateEntry.getX() >= 3) continue;
            TabEntry tabEntry = getEntryByPosition(updateEntry.getX(), updateEntry.getY());
            GameProfile gameProfile = createProfile(tabEntry);
            if (updateEntry.hasSkin() && !isLegacy) {
                Skin updateSkin = updateEntry.getSkin();
                Skin tabSkin = tabEntry.getSkin();
                if (!updateSkin.equals(tabSkin)) {
                    final PacketPlayerInfoWrapper infoWrapper =
                            new PacketPlayerInfoWrapper(PacketPlayerInfoWrapper.PlayerInfoAction.ADD_PLAYER);
                    infoWrapper.setProfile(gameProfile);
                    tabEntry.setSkin(updateSkin);
                    gameProfile.getProperties().put("textures",
                            new Property("textures", updateSkin.getValue(), updateSkin.getSignature()));
                    final int ping = updateEntry.getPing();
                    if (ping != tabEntry.getPing()) {
                        tabEntry.setPing(ping);
                        infoWrapper.setPing(ping);
                    } else
                        infoWrapper.setPing(tabEntry.getPing());
                    final String text = updateEntry.getText();
                    if (!text.equals(tabEntry.getText()) ) {
                        tabEntry.setText(text);
                        infoWrapper.setText(text);
                    } else {
                        infoWrapper.setText(tabEntry.getText());
                    }
                    infoWrapper.send(player);
                    return;
                }
            }
            String entryName = NAMES[updateEntry.getX()][updateEntry.getY()];
            final int ping = updateEntry.getPing();
            if (ping != tabEntry.getPing()) {
                tabEntry.setPing(ping);
                final PacketPlayerInfoWrapper infoWrapper =
                        new PacketPlayerInfoWrapper(PacketPlayerInfoWrapper.PlayerInfoAction.UPDATE_LATENCY);
                infoWrapper.setPing(ping);
                if (isLegacy) {
                    infoWrapper.setText(entryName);
                } else {
                    infoWrapper.setProfile(gameProfile);
                }
                infoWrapper.send(player);
            }
            String text = updateEntry.getText();
            if (isLegacy) {
                text = LegacyUtils.prefixAndSuffix(text);
                if (!text.equals(tabEntry.getText())) {
                    final PacketScoreboardTeamWrapper team = new PacketScoreboardTeamWrapper(false, entryName);
                    String[] split = text.split(":;:");
                    if (split.length > 1) {
                        team.setSuffix(split[1]);
                    }
                    team.setPrefix(split[0]);
                    team.send(player);
                }
            }else
            if (!text.equals(tabEntry.getText())) {
                final PacketPlayerInfoWrapper infoWrapper =
                        new PacketPlayerInfoWrapper(PacketPlayerInfoWrapper.PlayerInfoAction.UPDATE_DISPLAY_NAME);
                infoWrapper.setText(text);
                infoWrapper.setProfile(gameProfile);
                infoWrapper.send(player);
            }
            tabEntry.setText(text);
        }
    }

    public GameProfile createProfile(TabEntry tabEntry) {
        return new GameProfile(tabEntry.getId(), tabEntry.getEntryName());
    }

    private static String[][] NAMES;

    static {
        if (ServerVersion.isLegacy()) {
            NAMES = new String[3][20];
            ChatColor[] colors = ChatColor.values();
            StringBuilder sb = new StringBuilder("    " + ChatColor.RESET);
            for (int i = 0; i < 60; i++) {
                int x = i % 3;
                int y = i / 3;
                sb.setCharAt(0, ChatColor.COLOR_CHAR);
                sb.setCharAt(1, colors[x].getChar());
                sb.setCharAt(2, ChatColor.COLOR_CHAR);
                sb.setCharAt(3, colors[y].getChar());
                NAMES[x][y] = sb.toString();
            }
        }
    }

}
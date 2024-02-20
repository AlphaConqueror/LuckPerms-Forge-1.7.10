/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.forge;

import java.util.UUID;
import me.lucko.luckperms.common.cacheddata.result.TristateResult;
import me.lucko.luckperms.common.locale.TranslationManager;
import me.lucko.luckperms.common.query.QueryOptionsImpl;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.sender.SenderFactory;
import me.lucko.luckperms.common.verbose.VerboseCheckTarget;
import me.lucko.luckperms.common.verbose.event.CheckOrigin;
import me.lucko.luckperms.forge.capabilities.UserCapability;
import me.lucko.luckperms.forge.capabilities.UserCapabilityImpl;
import me.lucko.luckperms.forge.util.ChatMessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.luckperms.api.util.Tristate;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;

public class ForgeSenderFactory extends SenderFactory<LPForgePlugin, ICommandSender> {

    public ForgeSenderFactory(final LPForgePlugin plugin) {
        super(plugin);
    }

    public static IChatComponent toNativeText(final Component component) {
        return IChatComponent.Serializer.jsonToComponent(
                GsonComponentSerializer.gson().serialize(component));
    }

    @Override
    protected UUID getUniqueId(final ICommandSender commandSource) {
        if (commandSource instanceof EntityPlayer) {
            return ((EntityPlayer) commandSource).getUniqueID();
        }
        return Sender.CONSOLE_UUID;
    }

    @Override
    protected String getName(final ICommandSender commandSource) {
        if (commandSource instanceof EntityPlayer) {
            return commandSource.getCommandSenderName();
        }
        return Sender.CONSOLE_NAME;
    }

    @Override
    protected void sendMessage(final ICommandSender sender, final Component message) {
        final IChatComponent chatComponent = toNativeText(TranslationManager.render(message));

        for (final IChatComponent comp : ChatMessageUtil.fixLineJumps(chatComponent)) {
            sender.addChatMessage(comp);
        }
    }

    @Override
    protected Tristate getPermissionValue(final ICommandSender commandSource, final String node) {
        if (commandSource instanceof EntityPlayerMP) {
            final EntityPlayerMP player = (EntityPlayerMP) commandSource;
            final UserCapability user = UserCapabilityImpl.get(player);

            return user.checkPermission(node);
        }

        final VerboseCheckTarget target =
                VerboseCheckTarget.internal(commandSource.getCommandSenderName());
        this.getPlugin().getVerboseHandler()
                .offerPermissionCheckEvent(CheckOrigin.PLATFORM_API_HAS_PERMISSION, target,
                        QueryOptionsImpl.DEFAULT_CONTEXTUAL, node, TristateResult.UNDEFINED);
        this.getPlugin().getPermissionRegistry().offer(node);
        return Tristate.UNDEFINED;
    }

    @Override
    protected boolean hasPermission(final ICommandSender commandSource, final String node) {
        return this.getPermissionValue(commandSource, node).asBoolean();
    }

    @Override
    protected void performCommand(final ICommandSender sender, final String command) {
        this.getPlugin().getBootstrap().getServer().ifPresent(
                minecraftServer -> minecraftServer.getCommandManager()
                        .executeCommand(sender, command));
    }

    @Override
    protected boolean isConsole(final ICommandSender sender) {
        return sender instanceof MinecraftServer || sender instanceof RConConsoleSource;
    }
}

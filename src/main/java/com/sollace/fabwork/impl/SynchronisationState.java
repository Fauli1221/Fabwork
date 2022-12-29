package com.sollace.fabwork.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;

import com.sollace.fabwork.api.ModEntry;
import com.sollace.fabwork.api.client.ModProvisionCallback;

import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;

record SynchronisationState(
        List<ModEntryImpl> installedOnClient,
        List<ModEntryImpl> installedOnServer) {

    public SynchronisationState(Stream<ModEntryImpl> installedOnClient, Stream<ModEntryImpl> installedOnServer) {
        this(installedOnClient.toList(), installedOnServer.toList());
    }

    public boolean verify(ClientConnection connection, Logger logger) {
        Set<String> missingOnServer = getDifference(installedOnClient.stream().filter(c -> c.requirement().isRequiredOnServer()), installedOnServer);
        Set<String> missingOnClient = getDifference(installedOnServer.stream().filter(c -> c.requirement().isRequiredOnClient()), installedOnClient);

        installedOnServer.stream().forEach(entry -> {
            ModProvisionCallback.EVENT.invoker().onModProvisioned(entry, !missingOnClient.contains(entry.modId()));
        });

        if (!missingOnServer.isEmpty() || !missingOnClient.isEmpty()) {
            Text errorMessage = createErrorMessage(missingOnServer, missingOnClient);
            logger.error(errorMessage.getString());
            connection.disconnect(errorMessage);
            return false;
        }

        String[] installed = installedOnServer.stream().map(ModEntry::modId).toArray(String[]::new);

        logger.info("Connection succeeded with {} syncronised mod(s) [{}]", installed.length, String.join(", ", installed));
        return true;
    }

    private Set<String> getDifference(Stream<ModEntryImpl> provided, List<ModEntryImpl> required) {
        return provided
                .map(ModEntry::modId)
                .filter(id -> required.stream().filter(cc -> cc.modId().equalsIgnoreCase(id)).findAny().isEmpty())
                .distinct()
                .collect(Collectors.toSet());
    }

    private Text createErrorMessage(Set<String> missingOnServer, Set<String> missingOnClient) {
        String serverMissing = String.join(", ", missingOnServer.stream().toArray(CharSequence[]::new));
        String clientMissing = String.join(", ", missingOnClient.stream().toArray(CharSequence[]::new));

        if (missingOnClient.isEmpty()) {
            return Text.translatable("fabwork.error.server_missing_mods", serverMissing);
        }

        if (missingOnServer.isEmpty()) {
            return Text.translatable("fabwork.error.client_missing_mods", clientMissing);
        }

        return Text.translatable("fabwork.error.both_missing_mods", clientMissing, serverMissing);
    }
}
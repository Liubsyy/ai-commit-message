package com.liubs.aicommit.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@State(name = "AiCommitSettings", storages = @Storage("aiCommitMessage.xml"))
public final class AiCommitSettings implements PersistentStateComponent<AiCommitSettings.State> {

    public static class State {
        public List<ProviderProfile> profiles = new ArrayList<>();
        public String selectedProfileId = "";
        public int diffCharLimit = 8000;
        /** 随机安装标识，仅用于免费网关限流，不使用硬件或 JetBrains 账号信息。 */
        public String installationId = UUID.randomUUID().toString();
        public int managedFreeModelConfigVersion;
    }

    private State state = createDefaultState();

    public static AiCommitSettings getInstance() {
        return ServiceManager.getService(AiCommitSettings.class);
    }

    @NotNull
    @Override
    public State getState() {
        ensureManagedFreeProfile();
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        if (state.installationId == null || state.installationId.trim().isEmpty()) {
            state.installationId = UUID.randomUUID().toString();
        }
        ensureManagedFreeProfile();
        if (state.managedFreeModelConfigVersion < 1) {
            ProviderProfile managed = findProfile(ManagedFreeProvider.PROFILE_ID);
            if (managed != null) {
                managed.models = new ArrayList<>();
                managed.selectedModel = "";
            }
            state.managedFreeModelConfigVersion = 1;
        }
    }

    /** 当前生效的配置;selectedProfileId 失效时退回第一个 */
    @Nullable
    public ProviderProfile getSelectedProfile() {
        ProviderProfile byId = findProfile(state.selectedProfileId);
        if (byId != null) {
            return byId;
        }
        return state.profiles.isEmpty() ? null : state.profiles.get(0);
    }

    @Nullable
    public ProviderProfile findProfile(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (ProviderProfile p : state.profiles) {
            if (id.equals(p.id)) {
                return p;
            }
        }
        return null;
    }

    private static State createDefaultState() {
        State state = new State();
        ProviderProfile managed = ManagedFreeProvider.createProfile();
        state.profiles.add(managed);
        state.selectedProfileId = managed.id;
        state.managedFreeModelConfigVersion = 1;
        return state;
    }

    private void ensureManagedFreeProfile() {
        if (state.profiles == null) {
            state.profiles = new ArrayList<>();
        }
        ProviderProfile managed = null;
        for (ProviderProfile profile : state.profiles) {
            if (profile.isManagedFree()) {
                managed = profile;
                break;
            }
        }
        if (managed == null) {
            managed = ManagedFreeProvider.createProfile();
            state.profiles.add(0, managed);
        } else {
            boolean wasSelected = managed.id != null && managed.id.equals(state.selectedProfileId);
            ManagedFreeProvider.normalize(managed);
            if (wasSelected) {
                state.selectedProfileId = managed.id;
            }
        }
        if (findProfile(state.selectedProfileId) == null) {
            state.selectedProfileId = managed.id;
        }
    }
}

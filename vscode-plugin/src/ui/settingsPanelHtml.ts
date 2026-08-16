import * as vscode from 'vscode';

/** 设置面板的内联 HTML:结构与 JetBrains SettingsDialog 对齐(左列表 + 右表单) */
export function renderSettingsHtml(webview: vscode.Webview): string {
  const nonce = makeNonce();
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy"
      content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  :root { color-scheme: light dark; }
  body {
    font-family: var(--vscode-font-family);
    font-size: var(--vscode-font-size);
    color: var(--vscode-foreground);
    background: var(--vscode-editor-background);
    margin: 0; padding: 0; height: 100vh; display: flex; flex-direction: column;
  }
  .layout { display: flex; flex: 1; min-height: 0; }
  .left {
    width: 210px; min-width: 160px; display: flex; flex-direction: column;
    border-right: 1px solid var(--vscode-panel-border); background: var(--vscode-sideBar-background);
  }
  .toolbar { display: flex; gap: 2px; padding: 4px; border-bottom: 1px solid var(--vscode-panel-border); }
  .toolbar button {
    width: 26px; height: 24px; border: none; background: transparent;
    color: var(--vscode-foreground); cursor: pointer; border-radius: 3px; font-size: 14px;
  }
  .toolbar button:hover { background: var(--vscode-toolbar-hoverBackground); }
  #profileList { flex: 1; overflow-y: auto; margin: 0; padding: 4px 0; list-style: none; }
  #profileList li {
    padding: 4px 8px; cursor: pointer; display: flex; align-items: baseline; gap: 4px;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  #profileList li.selected {
    background: var(--vscode-list-activeSelectionBackground);
    color: var(--vscode-list-activeSelectionForeground);
  }
  #profileList li .check { width: 14px; flex: none; }
  #profileList li .model {
    font-size: 0.85em; opacity: 0.7; overflow: hidden; text-overflow: ellipsis;
  }
  #profileList .empty { opacity: 0.6; cursor: default; }
  .right { flex: 1; overflow-y: auto; padding: 12px 16px; }
  .row { display: flex; align-items: flex-start; margin-bottom: 10px; }
  .row > label { width: 120px; flex: none; padding-top: 4px; }
  .row > .field { flex: 1; display: flex; gap: 8px; }
  input[type=text], input[type=password], select, textarea {
    flex: 1; box-sizing: border-box;
    background: var(--vscode-input-background); color: var(--vscode-input-foreground);
    border: 1px solid var(--vscode-input-border, transparent); border-radius: 2px;
    padding: 4px 6px; font-family: inherit; font-size: inherit; outline: none;
  }
  textarea { font-family: var(--vscode-editor-font-family); min-height: 180px; resize: vertical; }
  input:focus, select:focus, textarea:focus { border-color: var(--vscode-focusBorder); }
  input:disabled, select:disabled, textarea:disabled { opacity: 0.5; }
  button.action {
    background: var(--vscode-button-secondaryBackground, var(--vscode-button-background));
    color: var(--vscode-button-secondaryForeground, var(--vscode-button-foreground));
    border: none; border-radius: 2px; padding: 4px 12px; cursor: pointer; white-space: nowrap;
  }
  button.action:hover { background: var(--vscode-button-secondaryHoverBackground, var(--vscode-button-hoverBackground)); }
  button.primary { background: var(--vscode-button-background); color: var(--vscode-button-foreground); }
  button.primary:hover { background: var(--vscode-button-hoverBackground); }
  button:disabled { opacity: 0.5; cursor: default; }
  .hint { font-size: 0.85em; opacity: 0.65; margin: 2px 0 0; }
  .button-row { margin-top: 8px; display: flex; gap: 8px; }
  .footer {
    display: flex; justify-content: flex-end; align-items: center; gap: 8px;
    padding: 8px 16px; border-top: 1px solid var(--vscode-panel-border);
  }
  #status { flex: 1; font-size: 0.9em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  #status.error { color: var(--vscode-errorForeground); }
  #status.ok { color: var(--vscode-charts-green, var(--vscode-foreground)); }
</style>
</head>
<body>
  <div class="layout">
    <div class="left">
      <div class="toolbar">
        <button id="addBtn" title="Add profile">+</button>
        <button id="removeBtn" title="Remove profile">−</button>
        <button id="dupBtn" title="Duplicate profile">⧉</button>
      </div>
      <ul id="profileList"></ul>
    </div>
    <div class="right">
      <div class="row"><label for="nameField">Name:</label>
        <div class="field"><input type="text" id="nameField"></div></div>
      <div class="row"><label for="baseUrlField">Base URL:</label>
        <div class="field"><input type="text" id="baseUrlField"></div></div>
      <div class="row"><label for="apiKeyField">API Key:</label>
        <div class="field"><input type="password" id="apiKeyField"></div></div>
      <div class="row"><label for="modelField">Model:</label>
        <div class="field">
          <input type="text" id="modelField" list="modelOptions">
          <select id="modelSelect" style="display:none"></select>
          <datalist id="modelOptions"></datalist>
          <button class="action" id="fetchBtn">Fetch From Provider</button>
        </div></div>
      <div class="row"><label for="languageField">Output language:</label>
        <div class="field"><select id="languageField"></select></div></div>
      <div class="row"><label for="promptField">Prompt:</label>
        <div class="field" style="flex-direction:column; align-items:stretch;">
          <textarea id="promptField" spellcheck="false"></textarea>
          <p class="hint">The built-in Conventional Commits template is prefilled and editable</p>
          <div class="button-row">
            <button class="action" id="testBtn">Test Connection</button>
            <button class="action" id="restorePromptBtn">Restore Default Prompt</button>
          </div>
        </div></div>
    </div>
  </div>
  <div class="footer">
    <span id="status"></span>
    <button class="action" id="cancelBtn">Cancel</button>
    <button class="action primary" id="saveBtn">Save</button>
  </div>
<script nonce="${nonce}">
(function () {
  const vscode = acquireVsCodeApi();
  const MANAGED_FREE = 'managed-free';

  let entries = [];
  let activeProfileId = '';
  let currentIndex = -1;
  let defaultPrompt = '';
  let languageCodes = [];

  const el = (id) => document.getElementById(id);
  const listEl = el('profileList');

  function uuid() {
    if (window.crypto && crypto.randomUUID) { return crypto.randomUUID(); }
    return 'p-' + Date.now() + '-' + Math.random().toString(16).slice(2);
  }

  function isManagedFree(p) { return p.type === MANAGED_FREE; }

  function setStatus(text, kind) {
    const status = el('status');
    status.textContent = text || '';
    status.className = kind || '';
  }

  function renderList() {
    listEl.textContent = '';
    if (entries.length === 0) {
      const li = document.createElement('li');
      li.className = 'empty';
      li.textContent = 'Click + to add a profile';
      listEl.appendChild(li);
      return;
    }
    entries.forEach((entry, i) => {
      const li = document.createElement('li');
      if (i === currentIndex) { li.classList.add('selected'); }
      const check = document.createElement('span');
      check.className = 'check';
      check.textContent = entry.profile.id === activeProfileId ? '✓' : '';
      const name = document.createElement('span');
      name.textContent = entry.profile.name.trim() || '(unnamed)';
      li.appendChild(check);
      li.appendChild(name);
      if (entry.profile.selectedModel) {
        const model = document.createElement('span');
        model.className = 'model';
        model.textContent = entry.profile.selectedModel;
        li.appendChild(model);
      }
      li.addEventListener('click', () => selectIndex(i));
      listEl.appendChild(li);
    });
  }

  function selectIndex(i) {
    if (i === currentIndex) { return; }
    saveForm(currentIndex);
    currentIndex = i;
    loadForm(i);
    renderList();
  }

  function setModelOptions(models, selected, managedFree) {
    const datalist = el('modelOptions');
    const select = el('modelSelect');
    const input = el('modelField');
    datalist.textContent = '';
    select.textContent = '';
    for (const m of models) {
      const option = document.createElement('option');
      option.value = m;
      option.textContent = m;
      datalist.appendChild(option.cloneNode(true));
      select.appendChild(option);
    }
    if (managedFree) {
      input.style.display = 'none';
      select.style.display = '';
      if (models.length === 0) {
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = '(no free models; click Fetch From Provider)';
        select.appendChild(placeholder);
      }
      select.value = models.includes(selected) ? selected : '';
    } else {
      select.style.display = 'none';
      input.style.display = '';
      input.value = selected || '';
    }
  }

  function currentModelValue(managedFree) {
    return (managedFree ? el('modelSelect').value : el('modelField').value).trim();
  }

  function loadForm(index) {
    const hasProfile = index >= 0 && index < entries.length;
    if (!hasProfile) {
      el('nameField').value = '';
      el('baseUrlField').value = '';
      el('apiKeyField').value = '';
      setModelOptions([], '', false);
      el('languageField').selectedIndex = 0;
      el('promptField').value = '';
    } else {
      const entry = entries[index];
      const p = entry.profile;
      el('nameField').value = p.name;
      el('baseUrlField').value = p.baseUrl;
      el('apiKeyField').value = entry.apiKey;
      setModelOptions(p.models, p.selectedModel, isManagedFree(p));
      el('languageField').value = languageCodes.includes(p.outputLanguage) ? p.outputLanguage : 'auto';
      el('promptField').value = (p.prompt && p.prompt.trim()) ? p.prompt : defaultPrompt;
      el('promptField').scrollTop = 0;
    }
    const managedFree = hasProfile && isManagedFree(entries[index].profile);
    for (const id of ['nameField', 'baseUrlField', 'apiKeyField', 'modelField', 'modelSelect',
                      'languageField', 'promptField', 'fetchBtn', 'testBtn', 'restorePromptBtn']) {
      el(id).disabled = !hasProfile;
    }
    if (managedFree) {
      el('nameField').disabled = true;
      el('baseUrlField').disabled = true;
      el('apiKeyField').disabled = true;
    }
  }

  function saveForm(index) {
    if (index < 0 || index >= entries.length) { return; }
    const entry = entries[index];
    const p = entry.profile;
    const managedFree = isManagedFree(p);
    if (!managedFree) {
      p.name = el('nameField').value.trim();
      p.baseUrl = el('baseUrlField').value.trim();
      entry.apiKey = el('apiKeyField').value.trim();
    }
    p.prompt = el('promptField').value;
    p.selectedModel = currentModelValue(managedFree);
    if (p.selectedModel && !p.models.includes(p.selectedModel)) {
      p.models.push(p.selectedModel);
    }
    p.outputLanguage = el('languageField').value || 'auto';
  }

  function snapshotCurrent() {
    saveForm(currentIndex);
    return entries[currentIndex];
  }

  el('addBtn').addEventListener('click', () => {
    saveForm(currentIndex);
    entries.push({
      profile: {
        id: uuid(), type: 'openai-compatible', name: 'New Profile',
        baseUrl: 'https://api.openai.com/v1', temperature: 0.7,
        prompt: defaultPrompt, outputLanguage: 'auto', models: [], selectedModel: '',
      },
      apiKey: '',
    });
    currentIndex = entries.length - 1;
    loadForm(currentIndex);
    renderList();
  });

  el('removeBtn').addEventListener('click', () => {
    if (currentIndex < 0) { return; }
    if (isManagedFree(entries[currentIndex].profile)) {
      setStatus('The built-in free provider cannot be removed.', 'error');
      return;
    }
    entries.splice(currentIndex, 1);
    currentIndex = entries.length === 0 ? -1 : Math.min(currentIndex, entries.length - 1);
    loadForm(currentIndex);
    renderList();
  });

  el('dupBtn').addEventListener('click', () => {
    if (currentIndex < 0) { return; }
    saveForm(currentIndex);
    const source = entries[currentIndex];
    if (isManagedFree(source.profile)) {
      setStatus('The built-in free provider cannot be duplicated.', 'error');
      return;
    }
    const copy = JSON.parse(JSON.stringify(source.profile));
    copy.id = uuid();
    copy.name = source.profile.name + ' (copy)';
    entries.push({ profile: copy, apiKey: source.apiKey });
    currentIndex = entries.length - 1;
    loadForm(currentIndex);
    renderList();
  });

  el('restorePromptBtn').addEventListener('click', () => {
    el('promptField').value = defaultPrompt;
    el('promptField').scrollTop = 0;
    el('promptField').focus();
  });

  el('fetchBtn').addEventListener('click', () => {
    if (currentIndex < 0) { return; }
    const entry = snapshotCurrent();
    if (!entry.profile.baseUrl) {
      setStatus('Fill in Base URL first', 'error');
      return;
    }
    el('fetchBtn').disabled = true;
    setStatus('Fetching models…', '');
    vscode.postMessage({ type: 'fetchModels', profile: entry.profile, apiKey: entry.apiKey });
  });

  el('testBtn').addEventListener('click', () => {
    if (currentIndex < 0) { return; }
    const entry = snapshotCurrent();
    if (!entry.profile.baseUrl) {
      setStatus('Fill in Base URL first', 'error');
      return;
    }
    el('testBtn').disabled = true;
    setStatus('Testing connection…', '');
    vscode.postMessage({ type: 'testConnection', profile: entry.profile, apiKey: entry.apiKey });
  });

  el('saveBtn').addEventListener('click', () => {
    saveForm(currentIndex);
    for (let i = 0; i < entries.length; i++) {
      const p = entries[i].profile;
      if (!isManagedFree(p) && (!p.name.trim() || !p.baseUrl.trim())) {
        selectIndex(i);
        setStatus('Name and Base URL must not be empty', 'error');
        return;
      }
    }
    vscode.postMessage({ type: 'save', entries });
  });

  el('cancelBtn').addEventListener('click', () => vscode.postMessage({ type: 'cancel' }));

  window.addEventListener('message', (event) => {
    const msg = event.data;
    if (msg.type === 'init') {
      entries = msg.entries;
      activeProfileId = msg.selectedProfileId;
      defaultPrompt = msg.defaultPrompt;
      languageCodes = msg.languageCodes;
      const languageField = el('languageField');
      languageField.textContent = '';
      msg.languageCodes.forEach((code, i) => {
        const option = document.createElement('option');
        option.value = code;
        option.textContent = msg.languageLabels[i];
        languageField.appendChild(option);
      });
      currentIndex = -1;
      let index = entries.length === 0 ? -1 : 0;
      for (let i = 0; i < entries.length; i++) {
        if (entries[i].profile.id === activeProfileId) { index = i; break; }
      }
      currentIndex = index;
      loadForm(index);
      renderList();
    } else if (msg.type === 'fetchModelsResult') {
      el('fetchBtn').disabled = currentIndex < 0;
      if (!msg.ok) {
        setStatus(msg.canceled ? '' : msg.error, msg.canceled ? '' : 'error');
        return;
      }
      const entry = entries[currentIndex];
      if (!entry) { return; }
      const managedFree = isManagedFree(entry.profile);
      const current = currentModelValue(managedFree);
      const models = msg.models.slice();
      // 与 JetBrains 版一致:保留手填模型;托管配置只认网关返回的列表
      if (!managedFree && current && !models.includes(current)) {
        models.push(current);
      }
      entry.profile.models = models;
      let selected;
      if (current && models.includes(current)) {
        selected = current;
      } else if (managedFree) {
        selected = '';
      } else {
        selected = msg.models[0];
      }
      entry.profile.selectedModel = selected;
      setModelOptions(models, selected, managedFree);
      setStatus('Fetched ' + msg.models.length + ' models', 'ok');
      renderList();
    } else if (msg.type === 'testConnectionResult') {
      el('testBtn').disabled = currentIndex < 0;
      if (msg.canceled) { setStatus('', ''); return; }
      setStatus(msg.message, msg.ok ? 'ok' : 'error');
    } else if (msg.type === 'validationError') {
      selectIndex(msg.index);
      setStatus(msg.message, 'error');
    }
  });

  vscode.postMessage({ type: 'ready' });
})();
</script>
</body>
</html>`;
}

function makeNonce(): string {
  let text = '';
  const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  for (let i = 0; i < 32; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
}

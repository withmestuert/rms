import { CLIENT_CONFIGURATION, ownerPath } from './apiPaths.js';
import { ROLE_ROOT, ROLE_TEST, STATUS_ACTIVE, STATUS_INACTIVE, AUTHORIZATION } from './constants.js';

const $ = id => document.getElementById(id);
let configuration;
let token = null; // Never persist bearer tokens in browser storage.

function message(text, error = false) {
  $('message').textContent = text;
  $('message').classList.toggle('error', error);
}
function signedOut() {
  token = null;
  $('workspace').hidden = true;
  $('logout').hidden = true;
  $('login').hidden = false;
  $('owners').replaceChildren();
  $('owner-select').replaceChildren();
  $('properties').replaceChildren();
  $('properties-section').hidden = true;
}
async function request(path, method = 'GET', body, extra = {}) {
  const headers = { 'Content-Type': 'application/json', ...extra };
  if (token) headers[AUTHORIZATION] = `Bearer ${token}`;
  const response = await fetch(path, { method, headers, credentials: 'omit', cache: 'no-store', body: body ? JSON.stringify(body) : undefined });
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    if (response.status === 401) signedOut();
    const detail = typeof data === 'string' ? data : data?.detail || data?.code || 'Request could not be completed';
    throw new Error(`${response.status}: ${detail}`);
  }
  return data;
}
function form(id, action) {
  $(id).addEventListener('submit', async event => {
    event.preventDefault();
    const button = event.currentTarget.querySelector('button');
    button.disabled = true;
    try { await action(Object.fromEntries(new FormData($(id)))); }
    catch (error) { message(error.message, true); }
    finally { button.disabled = false; }
  });
}
function actionButton(label, action) {
  const button = document.createElement('button');
  button.textContent = label;
  button.className = 'secondary';
  button.addEventListener('click', async () => {
    button.disabled = true;
    try { await action(); } catch (error) { message(error.message, true); }
    finally { button.disabled = false; }
  });
  return button;
}
async function loadOwners() {
  const owners = await request(configuration.paths.owners);
  $('owners').replaceChildren(); $('owner-select').replaceChildren();
  $('empty').hidden = owners.length !== 0;
  for (const owner of owners) {
    const row = document.createElement('tr');
    for (const value of [owner.fullName, `${owner.username} / ${owner.email}`, owner.status]) {
      const cell = document.createElement('td'); cell.textContent = value; row.append(cell);
    }
    const actions = document.createElement('td');
    const active = owner.status === STATUS_ACTIVE;
    actions.append(actionButton(active ? 'Deactivate' : 'Activate', async () => {
      await request(ownerPath(configuration.paths.ownerStatus, owner.id), 'PUT', { status: active ? STATUS_INACTIVE : STATUS_ACTIVE });
      await loadOwners(); message('Owner status updated. Previous sessions have been revoked.');
    }));
    actions.append(actionButton('View properties', async () => {
      const properties = await request(ownerPath(configuration.paths.ownerProperties, owner.id));
      $('properties').replaceChildren();
      for (const property of properties) { const item = document.createElement('li'); item.textContent = `${property.name} (${property.id})`; $('properties').append(item); }
      if (!properties.length) { const item = document.createElement('li'); item.textContent = 'This owner has not added a PG yet.'; $('properties').append(item); }
      $('properties-section').hidden = false;
    }));
    row.append(actions); $('owners').append(row);
    const option = document.createElement('option'); option.value = owner.id; option.textContent = `${owner.fullName} (@${owner.username})`; $('owner-select').append(option);
  }
}
async function enter() {
  const principal = await request(configuration.paths.me);
  if (![ROLE_ROOT, ROLE_TEST].includes(principal.role)) {
    await request(configuration.paths.logout, 'POST'); signedOut();
    throw new Error('Sign in with the root administrator account. PG owners use the main RMS application.');
  }
  $('login').hidden = true; $('setup').hidden = true; $('workspace').hidden = false; $('logout').hidden = false;
  await loadOwners(); message('Signed in. Manage PG owners below.');
}
form('setup-form', async data => {
  const { bootstrapKey, ...account } = data;
  await request(configuration.paths.bootstrap, 'POST', account, { [configuration.bootstrapHeader]: bootstrapKey });
  $('setup-form').reset(); $('setup').hidden = true;
  message('Root administrator created. Sign in to add PG owners.');
});
form('login-form', async data => {
  const session = await request(configuration.paths.login, 'POST', data);
  token = session.accessToken; $('login-form').reset(); await enter();
});
form('owner-form', async data => {
  await request(configuration.paths.owners, 'POST', data); $('owner-form').reset(); await loadOwners(); message('PG owner created. They can sign in to RMS and add their properties.');
});
form('reset-form', async ({ id, password }) => {
  await request(ownerPath(configuration.paths.ownerPassword, id), 'PUT', { password }); $('reset-form').reset(); message('Password reset. Previous owner sessions are revoked.');
});
form('password-form', async data => {
  await request(configuration.paths.password, 'POST', data); $('password-form').reset(); signedOut(); message('Password changed. Sign in again.');
});
$('logout').addEventListener('click', async () => {
  try { await request(configuration.paths.logout, 'POST'); signedOut(); message('Signed out.'); }
  catch (error) { message(`Sign out failed: ${error.message}. Retry to revoke the server session.`, true); }
});
$('refresh').addEventListener('click', () => loadOwners().catch(error => message(error.message, true)));
try {
  configuration = await request(CLIENT_CONFIGURATION);
  $('setup').hidden = !configuration.setupAvailable;
  $('testing').hidden = configuration.authenticationEnabled;
  if (!configuration.authenticationEnabled) await enter();
  else message(configuration.setupAvailable ? 'Complete first-time setup or sign in.' : 'Sign in with your root administrator account.');
} catch (error) { message(error.message, true); }

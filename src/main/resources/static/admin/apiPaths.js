// Remaining paths are supplied by the backend's canonical ApiPaths configuration.
export const CLIENT_CONFIGURATION = '/api/client-config';
export const ownerPath = (template, id) => template.replace('{id}', encodeURIComponent(id));

/* Service worker da TaskGO: só recebe os avisos (push) e abre o site no lugar certo ao tocar. */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));

self.addEventListener('push', e => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch (err) { d = { titulo: 'TaskGO', texto: e.data ? e.data.text() : '' }; }
  e.waitUntil(self.registration.showNotification(d.titulo || 'TaskGO', {
    body: d.texto || '',
    tag: d.tag || undefined,
    icon: '/assets/marca/icone-192.png',
    badge: '/assets/marca/icone-192.png',
    data: { url: d.url || '/#/app/notificacoes' }
  }));
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  const alvo = new URL((e.notification.data && e.notification.data.url) || '/#/app/notificacoes', self.location.origin).href;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(janelas => {
    for (const j of janelas) {
      if (new URL(j.url).origin === self.location.origin) { j.navigate(alvo); return j.focus(); }
    }
    return self.clients.openWindow(alvo);
  }));
});

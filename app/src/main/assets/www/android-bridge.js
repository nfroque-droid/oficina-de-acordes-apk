/* Ponte Android: substitui as capacidades do artefacto que fazem sentido fora do claude.ai.
   - downloads.save({filename, data}) → guarda na pasta Transferências (ou imprime, se for a página HTML de impressão).
   - Todas as outras capacidades (db, user, sample) não existem na app: a página usa o modo local, como num browser normal. */
(function () {
  if (!window.OficinaAndroid) return;
  var A = window.OficinaAndroid;
  function fail(code) { var e = new Error(code); e.code = code; return e; }
  function toBase64(data) {
    return new Promise(function (resolve, reject) {
      var blob = data instanceof Blob ? data : new Blob([data], { type: 'text/plain' });
      var fr = new FileReader();
      fr.onload = function () { var s = String(fr.result); resolve(s.slice(s.indexOf(',') + 1)); };
      fr.onerror = function () { reject(fail('fail')); };
      fr.readAsDataURL(blob);
    });
  }
  function mimeFor(name, data) {
    if (data instanceof Blob && data.type) return data.type;
    var ext = (name.split('.').pop() || '').toLowerCase();
    return { json: 'application/json', pdf: 'application/pdf', html: 'text/html', svg: 'image/svg+xml', png: 'image/png', txt: 'text/plain', csv: 'text/csv' }[ext] || 'application/octet-stream';
  }
  var downloads = {
    save: function (o) {
      var name = String((o && o.filename) || 'ficheiro');
      var data = o ? o.data : '';
      var mime = mimeFor(name, data);
      if (mime === 'text/html') {
        var asText = data instanceof Blob ? data.text() : Promise.resolve(String(data));
        return asText.then(function (html) {
          if (!A.printHtml(html, name.replace(/\.html?$/i, ''))) throw fail('fail');
        });
      }
      return toBase64(data).then(function (b64) {
        var r = A.saveFile(name, mime, b64);
        if (r === 'declined') throw fail('declined');
        if (r !== 'ok') throw fail('fail');
      });
    }
  };
  window.claude = window.claude || {};
  window.claude.use = function (cap) { return Promise.resolve(cap === 'downloads' ? downloads : null); };
})();

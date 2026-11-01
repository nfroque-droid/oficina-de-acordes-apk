#!/usr/bin/env python3
"""Prepara a página da Oficina de Acordes para correr dentro da APK, sem internet.

Lê web/oficina.html (a página tal como está publicada) e escreve
app/src/main/assets/www/index.html com três mudanças:
  1. as fontes do Google Fonts passam a vir de fonts.css (ficheiros locais);
  2. as bibliotecas de PDF (jsPDF, svg2pdf, html2canvas) passam a vir de libs/;
  3. carrega android-bridge.js antes de tudo, para as transferências e a impressão.

O código e a estrutura da app não mudam. Para atualizar a APK, basta substituir
web/oficina.html pela versão nova e fazer push: o GitHub Actions corre este script.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'web' / 'oficina.html'
DST = ROOT / 'app' / 'src' / 'main' / 'assets' / 'www' / 'index.html'

LIBS = {
    r'https://cdnjs\.cloudflare\.com/ajax/libs/jspdf/[^"\'\s]+/jspdf\.umd\.min\.js': 'libs/jspdf.umd.min.js',
    r'https://cdn\.jsdelivr\.net/npm/svg2pdf\.js@[^"\'\s/]+/dist/svg2pdf\.umd\.min\.js': 'libs/svg2pdf.umd.min.js',
    r'https://cdnjs\.cloudflare\.com/ajax/libs/html2canvas/[^"\'\s]+/html2canvas\.min\.js': 'libs/html2canvas.min.js',
}
FONTS = r'https://fonts\.googleapis\.com/css2\?[^"\']+'
# alphaTab (tablaturas): local na APK; na HTML aberta como ficheiro (file://) continua a usar a CDN,
# porque o browser não deixa ler a soundfont a partir de file://.
AT_LINE = 'const AT_BASE = AT_CDN;'
AT_LOCAL = "const AT_BASE = location.protocol === 'file:' ? AT_CDN : 'libs/alphatab/';"


def main() -> int:
    if not SRC.exists():
        print(f'Falta {SRC}', file=sys.stderr)
        return 1
    html = SRC.read_text(encoding='utf-8')

    html, n = re.subn(FONTS, 'fonts.css', html)
    print(f'fontes: {n} ligação(ões) trocadas por fonts.css')

    for pat, local in LIBS.items():
        html, n = re.subn(pat, local, html)
        print(f'{local}: {n} referência(s)')

    n = html.count(AT_LINE)
    html = html.replace(AT_LINE, AT_LOCAL)
    print(f'libs/alphatab/: {n} referência(s)')

    left = [u for u in re.findall(r'https://(?:cdn\.jsdelivr\.net|cdnjs\.cloudflare\.com|unpkg\.com)[^"\'\s`]*', html) if '@coderline/alphatab' not in u]
    if left:
        print('Aviso: ainda há bibliotecas na internet (a app precisa de rede para estas):', *sorted(set(left)), sep='\n  ')

    bridge = '<script src="android-bridge.js"></script>'
    if bridge not in html:
        html, n = re.subn(r'<head>', '<head>' + bridge, html, count=1, flags=re.I)
        if n != 1:
            print('Não encontrei <head> na página.', file=sys.stderr)
            return 1

    DST.parent.mkdir(parents=True, exist_ok=True)
    DST.write_text(html, encoding='utf-8')
    print(f'Escrito {DST.relative_to(ROOT)} ({len(html):,} bytes)')
    return 0


if __name__ == '__main__':
    sys.exit(main())

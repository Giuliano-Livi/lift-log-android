"""Turn web/lift-log.html (the Claude artifact source) into the app's offline page.

Swaps the Google Fonts link for the bundled font files and adds the document
skeleton the artifact host normally provides. Run before building:
    python3 tools/build_page.py
"""
import pathlib, re

root = pathlib.Path(__file__).resolve().parent.parent
src = (root / 'web' / 'lift-log.html').read_text(encoding='utf-8')

faces = [
    ('Poppins', 400, 'poppins-latin-400-normal'),
    ('Poppins', 500, 'poppins-latin-500-normal'),
    ('Poppins', 600, 'poppins-latin-600-normal'),
    ('Poppins', 700, 'poppins-latin-700-normal'),
]
font_css = '\n'.join(
    "@font-face{font-family:'%s';font-style:normal;font-weight:%d;font-display:swap;src:url(fonts/%s.woff2) format('woff2');}" % f
    for f in faces)

body, n = re.subn(r'<link rel="stylesheet" href="https://fonts\.googleapis\.com[^>]*>', '<style>%s</style>' % font_css, src)
if n != 1:
    raise SystemExit('Google Fonts link not found in web/lift-log.html')

reset = (':root{color-scheme:dark;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}'
         'body{margin:0;font:14px system-ui,sans-serif}img{max-width:100%}[hidden]{display:none!important}')
page = ('<!doctype html><html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
        '<style>%s</style></head><body>\n%s\n</body></html>\n' % (reset, body))
out = root / 'app' / 'src' / 'main' / 'assets' / 'index.html'
out.write_text(page, encoding='utf-8')
print('wrote', out.relative_to(root), len(page), 'bytes')

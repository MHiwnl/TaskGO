"""Captura as telas reais do TaskGO em modo demonstração (celular 390x844, 2x).

Uso (com o site local em http://localhost:3000):
    python capturar_telas.py
Saída: telas/*.png nesta pasta.
"""
import pathlib
import sys

from playwright.sync_api import sync_playwright

BASE = "http://localhost:3000/index.html"
SAIDA = pathlib.Path(__file__).resolve().parent / "telas"
SAIDA.mkdir(exist_ok=True)

# (arquivo, papel, rota após o #)
ROTAS = [
    ("01-cliente-inicio", "cliente", "/app/inicio"),
    ("02-cliente-contratar", "cliente", "/app/contratar"),
    ("03-cliente-pedidos", "cliente", "/app/pedidos"),
    ("04-cliente-pedido", "cliente", "/app/pedido/{id}"),
    ("05-cliente-rastreio", "cliente", "/app/chamando/{id}"),
    ("06-cliente-pagamento", "cliente", "/app/pagar/{id}"),
    ("07-cliente-avaliacoes", "cliente", "/app/avaliacoes"),
    ("08-profissional-oportunidades", "profissional", "/app/oportunidades"),
    ("09-profissional-agenda", "profissional", "/app/agenda"),
]


def pagina(browser, papel):
    ctx = browser.new_context(
        viewport={"width": 390, "height": 844},
        device_scale_factor=2,
        is_mobile=True,
        has_touch=True,
        locale="pt-BR",
    )
    page = ctx.new_page()
    erros = []
    page.on("pageerror", lambda e: erros.append(str(e)))
    page.goto(f"{BASE}?demo=1&role={papel}#/app/inicio", wait_until="networkidle")
    page.wait_for_timeout(800)
    return ctx, page, erros


LIMPAR_TEMPO = """() => {
  // Regra do filme: nada de relógio, cronômetro ou contagem de tempo na tela.
  // Oculta só o texto de tempo (hora, "há N min", "— min") e a caixa de espera com relógio.
  const padrao = /\\b\\d{1,2}:\\d{2}\\b|há \\d+ min|^—?\\s*min$/;
  for (const el of document.querySelectorAll('body *')) {
    if (el.childElementCount === 0 && padrao.test(el.textContent.trim())) el.style.visibility = 'hidden';
  }
  // atribuição do mapa (Leaflet/OpenStreetMap): terceiros, fora do filme
  document.querySelectorAll('.leaflet-control-attribution').forEach(e => e.style.visibility = 'hidden');
  // caixa de espera com ícone de relógio: oculta o menor bloco que tem o texto e um svg
  const achados = [...document.querySelectorAll('body *')].filter(e =>
    /Aguardando a chegada/.test(e.textContent) && e.querySelector('svg') && e.textContent.length < 120);
  for (const e of achados) {
    if (!achados.some(o => o !== e && e.contains(o))) e.style.visibility = 'hidden';
  }
}"""


def ir(page, rota):
    page.evaluate("h => { location.hash = h; }", "#" + rota)
    page.wait_for_timeout(1200)
    page.evaluate(LIMPAR_TEMPO)


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        ctx_c, page_c, erros_c = pagina(browser, "cliente")
        ctx_p, page_p, erros_p = pagina(browser, "profissional")

        # Descobre um pedido de demonstração pelo próprio DOM da lista.
        ir(page_c, "/app/pedidos")
        hrefs = page_c.eval_on_selector_all(
            'a[href*="#/app/pedido/"]', "els => els.map(e => e.getAttribute('href'))"
        )
        if not hrefs:
            print("Nenhum pedido encontrado na lista de demonstração.")
            sys.exit(1)
        pid = hrefs[0].split("#/app/pedido/")[1].split("?")[0].split("/")[0]
        print("pedido de demonstração:", pid)

        for arq, papel, rota in ROTAS:
            page = page_c if papel == "cliente" else page_p
            erros = erros_c if papel == "cliente" else erros_p
            ir(page, rota.replace("{id}", pid))
            page.screenshot(path=str(SAIDA / f"{arq}.png"))
            print("ok", arq, "| erros:", len(erros))

        # Mesma tela, rolada até a parte que o filme mostra (o código e o botão de aceite).
        ir(page_c, f"/app/pedido/{pid}")
        page_c.get_by_text("Seu código de segurança").first.scroll_into_view_if_needed()
        page_c.wait_for_timeout(400)
        page_c.screenshot(path=str(SAIDA / "04b-cliente-pedido-codigo.png"))
        # O código muda a cada carregamento: o filme usa este mesmo valor nos dígitos.
        codigo = page_c.evaluate(
            "() => [...document.querySelectorAll('*')]"
            ".map(e => e.childElementCount === 0 ? e.textContent.trim() : '')"
            ".find(t => /^\\d{4}$/.test(t)) || ''"
        )
        (SAIDA / "codigo.json").write_text('{"codigo": "%s"}\n' % codigo, encoding="utf-8")
        print("ok 04b-cliente-pedido-codigo | codigo:", codigo)

        ir(page_p, "/app/oportunidades")
        page_p.get_by_text("Aceitar R$").first.scroll_into_view_if_needed()
        page_p.wait_for_timeout(400)
        page_p.screenshot(path=str(SAIDA / "08b-profissional-aceitar.png"))
        print("ok 08b-profissional-aceitar")

        browser.close()


if __name__ == "__main__":
    main()

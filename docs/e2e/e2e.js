// Roteiro E2E no Chromium contra a aplicação rodando em container (simulação do deploy).
// Uso: node e2e.js <baseUrl> <pastaPrints> <senhaAdmin>
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE = process.argv[2] || 'http://localhost:10000';
const OUT = process.argv[3] || './prints';
const SENHA_ADMIN = process.argv[4] || 'Admin@123';
fs.mkdirSync(OUT, { recursive: true });

const resultados = [];
const errosConsole = [];
function ok(cond, msg) {
  resultados.push({ ok: !!cond, msg });
  console.log(`${cond ? 'PASS' : 'FAIL'} - ${msg}`);
}
async function print(page, nome) {
  await page.screenshot({ path: path.join(OUT, nome), fullPage: true });
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
  const context = await browser.newContext({ viewport: { width: 1366, height: 768 }, locale: 'pt-BR' });
  const page = await context.newPage();
  page.on('console', (m) => { if (m.type() === 'error') errosConsole.push(`${page.url()} :: ${m.text()}`); });
  page.on('pageerror', (e) => errosConsole.push(`${page.url()} :: ${e.message}`));

  const sufixo = Date.now();
  const emailNovo = `ana.${sufixo}@teste.com`;

  // 1. Landing page
  await page.goto(BASE + '/');
  ok(await page.title() === 'Início | Mercado Express', 'Landing page (index.html) carrega');
  ok(await page.locator('a', { hasText: 'Criar conta grátis' }).isVisible(), 'Landing mostra botões Entrar / Criar conta');
  ok(await page.getByTestId('credencial-admin').innerText().then(t => t.includes('admin@mercadoexpress.com') && t.includes(SENHA_ADMIN)),
     'Landing mostra e-mail e senha do Administrador');
  ok(await page.getByTestId('credencial-cliente').innerText().then(t => t.includes('cliente@mercadoexpress.com') && t.includes('Cliente@123')),
     'Landing mostra e-mail e senha do Cliente');
  await print(page, '01-landing-page.png');

  // 2. Rota restrita sem login -> tela de login personalizada
  await page.goto(BASE + '/produtos');
  ok(page.url().endsWith('/login'), 'Rota restrita redireciona visitante para /login');
  ok(await page.locator('h1', { hasText: 'Entrar' }).isVisible(), 'Tela de login personalizada exibida');
  ok(await page.getByTestId('credenciais').isVisible(), 'Contas de avaliação aparecem abaixo do formulário de login');
  await print(page, '02-tela-login.png');

  // 3. Login com e-mail sem conta -> Sign Up
  await page.fill('#email', emailNovo);
  await page.fill('#senha', 'qualquer123');
  await page.click('button[type=submit]');
  await page.waitForURL(/\/cadastro/);
  ok(await page.getByTestId('aviso-sem-conta').isVisible(), 'Usuário sem login é levado ao Sign Up com aviso');
  ok(await page.inputValue('#email') === emailNovo, 'E-mail já vem preenchido no Sign Up');

  // 4. Sign Up com erros de validação
  await page.fill('#nome', 'Ana Cliente');
  await page.fill('#senha', '123');
  await page.fill('#confirmacaoSenha', '456');
  await page.click('button[type=submit]');
  ok(await page.locator('.erro', { hasText: 'entre 6 e 72' }).isVisible(), 'Sign Up valida senha curta');
  ok(await page.locator('.erro', { hasText: 'não conferem' }).isVisible(), 'Sign Up valida confirmação de senha');
  await print(page, '03-signup-validacao.png');

  // 5. Sign Up válido
  await page.fill('#senha', 'Ana@2026');
  await page.fill('#confirmacaoSenha', 'Ana@2026');
  // mostrar/ocultar senha (JS externo, permitido pela CSP)
  await page.click('[data-toggle-senha="senha"]');
  ok(await page.getAttribute('#senha', 'type') === 'text', 'Botão Mostrar senha funciona (JS sem violar CSP)');
  await print(page, '04-signup-preenchido.png');
  await page.click('button[type=submit]');
  await page.waitForURL(/\/login/);
  ok(await page.locator('.alerta--sucesso', { hasText: 'Cadastro realizado' }).isVisible(), 'Cadastro concluído e volta ao login');
  ok(await page.inputValue('#email') === emailNovo, 'Login já vem com o e-mail cadastrado');
  await print(page, '05-cadastro-sucesso-login.png');

  // 6. Login como o novo CLIENTE
  await page.fill('#senha', 'Ana@2026');
  await page.click('button[type=submit]');
  await page.waitForURL(/\/produtos$/);
  ok(true, 'CLIENTE logado é redirecionado para /produtos');
  ok(await page.getByTestId('usuario-logado').innerText().then(t => t.includes('Ana Cliente') && t.includes('Cliente')), 'Menu mostra nome e perfil Cliente');
  ok(await page.locator('text=Excluir').count() === 0, 'CLIENTE não vê botões Editar/Excluir');
  ok(await page.locator('text=+ Novo produto').count() === 0, 'CLIENTE não vê botão Novo produto');
  await print(page, '06-cliente-catalogo.png');

  // 7. Busca
  await page.fill('#q', 'limpeza');
  await page.click('form.busca button[type=submit]');
  await page.waitForURL(/q=limpeza/);
  ok(await page.locator('[data-testid=tabela-produtos] tbody tr').count() === 2, 'Busca "limpeza" retorna 2 produtos');
  await print(page, '07-cliente-busca.png');

  // 8. Detalhe
  await page.click('text=Detergente Neutro');
  await page.waitForURL(/\/produtos\/\d+$/);
  ok(await page.getByTestId('detalhe-produto').isVisible(), 'Detalhe do produto exibido (READ)');
  await print(page, '08-cliente-detalhe.png');

  // 9. Cliente tenta área admin -> 403
  const resp403 = await page.goto(BASE + '/admin/painel');
  ok(resp403.status() === 403 && await page.getByTestId('pagina-403').isVisible(), 'CLIENTE recebe 403 em /admin/painel');
  await print(page, '09-cliente-acesso-negado.png');
  const resp403b = await page.goto(BASE + '/produtos/novo');
  ok(resp403b.status() === 403, 'CLIENTE recebe 403 em /produtos/novo');

  // 10. Logout
  await page.goto(BASE + '/produtos');
  await page.click('button:has-text("Sair")');
  await page.waitForURL(/\?saiu/);
  ok(await page.locator('text=Você saiu da sua conta').isVisible(), 'Logout volta para a landing com mensagem');

  // 11. Login ADMIN
  await page.goto(BASE + '/login');
  await page.fill('#email', 'admin@mercadoexpress.com');
  await page.fill('#senha', 'senha-errada');
  await page.click('button[type=submit]');
  await page.waitForURL(/\/login\?erro/);
  ok(await page.getByTestId('erro-login').isVisible(), 'Senha errada mostra erro no login');
  await print(page, '10-login-senha-errada.png');
  await page.fill('#email', 'admin@mercadoexpress.com');
  await page.fill('#senha', SENHA_ADMIN);
  await page.click('button[type=submit]');
  await page.waitForURL(/\/admin\/painel$/);
  ok(true, 'ADMIN logado é redirecionado para /admin/painel');
  ok(await page.getByTestId('kpi-produtos').innerText() === '12', 'Painel mostra 12 produtos');
  ok(await page.getByTestId('lista-estoque-baixo').innerText().then(t => t.includes('Alface Crespa')), 'Painel lista estoque baixo');
  await print(page, '11-admin-painel.png');

  // 12. Lista do admin com botões
  await page.click('nav >> text=Produtos');
  await page.waitForURL(/\/produtos$/);
  ok(await page.locator('[data-testid=tabela-produtos] >> text=Excluir').count() === 12, 'ADMIN vê Editar/Excluir em todos os produtos');
  await print(page, '12-admin-lista-produtos.png');

  // 13. CREATE com validação
  await page.click('[data-testid=botao-novo]');
  await page.waitForURL(/\/produtos\/novo$/);
  await page.click('form[data-testid=form-produto] button[type=submit]');
  ok(await page.locator('text=Corrija os campos destacados').isVisible(), 'CREATE vazio mostra erros de validação');
  await print(page, '13-admin-create-validacao.png');
  await page.fill('#nome', 'Abacaxi Pérola');
  await page.fill('#tipo', 'Fruta');
  await page.fill('#setor', 'Hortifruti');
  await page.fill('#tamanho', 'Unidade');
  await page.fill('#preco', '7.49');
  await page.fill('#quantidadeEstoque', '25');
  await page.fill('#descricao', 'Abacaxi doce, colhido na semana.');
  await print(page, '14-admin-create-preenchido.png');
  await page.click('form[data-testid=form-produto] button[type=submit]');
  await page.waitForURL(/\/produtos\/\d+$/);
  ok(await page.locator('.alerta--sucesso', { hasText: 'cadastrado com sucesso' }).isVisible(), 'CREATE grava e mostra mensagem');
  ok(await page.locator('.detalhe__preco').innerText() === 'R$ 7,49', 'Preço formatado em reais');
  await print(page, '15-admin-create-sucesso.png');
  const idNovo = page.url().split('/').pop();

  // 14. UPDATE
  await page.click('.detalhe__acoes >> text=Editar');
  await page.waitForURL(/\/editar$/);
  ok(await page.inputValue('#nome') === 'Abacaxi Pérola', 'Formulário de edição carrega os dados');
  await page.fill('#preco', '6.99');
  await page.fill('#quantidadeEstoque', '3');
  await print(page, '16-admin-update-form.png');
  await page.click('form[data-testid=form-produto] button[type=submit]');
  await page.waitForURL(new RegExp(`/produtos/${idNovo}$`));
  ok(await page.locator('.alerta--sucesso', { hasText: 'atualizado' }).isVisible(), 'UPDATE (PUT) grava e mostra mensagem');
  ok(await page.locator('.detalhe__preco').innerText() === 'R$ 6,99', 'Preço atualizado');
  ok(await page.locator('text=estoque baixo').isVisible(), 'Selo de estoque baixo aparece após atualização');
  await print(page, '17-admin-update-sucesso.png');

  // 15. DELETE (com confirmação; primeiro cancela, depois confirma)
  page.once('dialog', d => d.dismiss());
  await page.click('.detalhe__acoes >> text=Excluir');
  await page.waitForTimeout(300);
  ok(page.url().endsWith(`/produtos/${idNovo}`), 'Cancelar a confirmação não exclui');
  page.once('dialog', d => d.accept());
  await page.click('.detalhe__acoes >> text=Excluir');
  await page.waitForURL(/\/produtos$/);
  ok(await page.locator('.alerta--sucesso', { hasText: 'excluído' }).isVisible(), 'DELETE remove o produto');
  ok(await page.locator('[data-testid=tabela-produtos]', { hasText: 'Abacaxi Pérola' }).count() === 0, 'Produto excluído some da tabela');
  await print(page, '18-admin-delete-sucesso.png');
  const resp404 = await page.goto(BASE + '/produtos/' + idNovo);
  ok(resp404.status() === 404 && await page.getByTestId('pagina-404').isVisible(), 'Produto excluído retorna página 404');

  // 16. Gestão de usuários: promover a Ana
  await page.goto(BASE + '/admin/usuarios');
  const linhaAna = page.locator('tr', { hasText: emailNovo });
  ok(await linhaAna.locator('.selo--cliente').isVisible(), 'Nova usuária aparece como Cliente');
  await linhaAna.locator('button', { hasText: 'Tornar administrador' }).click();
  await page.waitForURL(/\/admin\/usuarios$/);
  ok(await page.locator('.alerta--sucesso', { hasText: 'agora é Administrador' }).isVisible(), 'ADMIN promove cliente a administrador');
  ok(await page.locator('tr', { hasText: emailNovo }).locator('.selo--admin').isVisible(), 'Perfil atualizado na tabela');
  await print(page, '19-admin-usuarios.png');

  // 16b. Login pelo botão "Preencher" (conta de avaliação do cliente)
  await page.click('button:has-text("Sair")');
  await page.waitForURL(/\?saiu/);
  await page.goto(BASE + '/login');
  await page.getByTestId('credencial-cliente').locator('button', { hasText: 'Preencher' }).click();
  ok(await page.inputValue('#email') === 'cliente@mercadoexpress.com', 'Botão Preencher coloca o e-mail do cliente');
  await page.click('form.formulario button[type=submit]');
  await page.waitForURL(/\/produtos$/);
  ok(true, 'Login com o botão Preencher (cliente) leva ao catálogo');
  await page.click('button:has-text("Sair")');
  await page.waitForURL(/\?saiu/);
  await page.goto(BASE + '/login');
  await page.getByTestId('credencial-admin').locator('button', { hasText: 'Preencher' }).click();
  await page.click('form.formulario button[type=submit]');
  await page.waitForURL(/\/admin\/painel$/);
  ok(true, 'Login com o botão Preencher (admin) leva ao painel');
  await page.goto(BASE + '/admin/usuarios');
  ok(await page.locator('tr', { hasText: 'cliente@mercadoexpress.com' }).locator('button').count() === 0,
     'Conta de avaliação do cliente não tem botões de excluir/alterar');

  // 17. Visual no celular
  const mobile = await browser.newContext({ viewport: { width: 375, height: 740 }, isMobile: true, locale: 'pt-BR' });
  const pm = await mobile.newPage();
  pm.on('pageerror', (e) => errosConsole.push(`mobile :: ${e.message}`));
  await pm.goto(BASE + '/');
  await pm.click('[data-menu-toggle]');
  ok(await pm.locator('#menu-principal').isVisible(), 'Menu responsivo abre no celular');
  await pm.screenshot({ path: path.join(OUT, '20-mobile-landing.png'), fullPage: false });
  await pm.goto(BASE + '/login');
  await pm.screenshot({ path: path.join(OUT, '21-mobile-login.png'), fullPage: false });

  // As respostas 403/404 provocadas de propósito pelo roteiro aparecem no console como
  // "Failed to load resource"; qualquer outra mensagem (violação de CSP, erro de JS) reprova.
  const errosReais = errosConsole.filter(e => !/Failed to load resource: the server responded with a status of (403|404)/.test(e));
  const esperados = errosConsole.length - errosReais.length;
  ok(esperados === 3, `Somente os 3 status 403/404 provocados pelo roteiro aparecem no console (${esperados})`);
  ok(errosReais.length === 0, `Nenhuma violação de CSP ou erro de JavaScript ${errosReais.length ? JSON.stringify(errosReais) : ''}`);

  await browser.close();
  const falhas = resultados.filter(r => !r.ok);
  console.log(`\nRESUMO: ${resultados.length - falhas.length}/${resultados.length} verificações passaram.`);
  fs.writeFileSync(path.join(OUT, '..', 'e2e-resultado.json'), JSON.stringify({ emailNovo, resultados }, null, 2));
  process.exit(falhas.length ? 1 : 0);
})().catch(async (e) => {
  console.error('ERRO NO ROTEIRO:', e);
  process.exit(2);
});

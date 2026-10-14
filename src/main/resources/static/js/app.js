// Mercado Express - comportamentos da interface (sem scripts inline, por causa da CSP)
document.addEventListener("DOMContentLoaded", () => {

    // Confirmação antes de excluir (formulários com data-confirm)
    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (evento) => {
            if (!window.confirm(form.dataset.confirm)) {
                evento.preventDefault();
            }
        });
    });

    // Mostrar / ocultar senha
    document.querySelectorAll("[data-toggle-senha]").forEach((botao) => {
        botao.addEventListener("click", () => {
            const campo = document.getElementById(botao.dataset.toggleSenha);
            if (!campo) return;
            const mostrar = campo.type === "password";
            campo.type = mostrar ? "text" : "password";
            botao.textContent = mostrar ? "Ocultar" : "Mostrar";
            botao.setAttribute("aria-label", mostrar ? "Ocultar senha" : "Mostrar senha");
        });
    });

    // Contas de avaliação: preenche o formulário de login com um clique
    document.querySelectorAll("[data-preencher-email]").forEach((botao) => {
        botao.addEventListener("click", () => {
            const email = document.getElementById("email");
            const senha = document.getElementById("senha");
            if (!email || !senha) return;
            email.value = botao.dataset.preencherEmail;
            senha.value = botao.dataset.preencherSenha;
            senha.focus();
        });
    });

    // Menu no celular
    const toggle = document.querySelector("[data-menu-toggle]");
    const menu = document.getElementById("menu-principal");
    if (toggle && menu) {
        toggle.addEventListener("click", () => {
            const aberto = menu.classList.toggle("menu--aberto");
            toggle.setAttribute("aria-expanded", String(aberto));
        });
    }
});

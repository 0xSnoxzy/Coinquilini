import { Component, effect, inject } from "@angular/core";
import {
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from "@angular/router";
import { Store } from "./core/store";
import { Auth } from "./pages/auth";
import { Onboarding } from "./pages/onboarding";
import { applyTheme } from "./core/theme";
@Component({
  selector: "app-root",
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, Auth, Onboarding],
  template: `<div id="cq-app">
    @if (!s.ready()) {
      <div class="loading" role="status">Apriamo casa…</div>
    } @else if (!s.user()) {
      <cq-auth />
    } @else {
      <div class="app-shell">
        <aside class="sidebar">
          <a class="brand" routerLink="/"
            ><span class="logo">⌂</span>Coinquilini</a
          >
          @if (s.house()) {
            <div class="house-tag">
              <span class="emoji">{{ s.house()?.emoji }}</span>
              <div>
                <strong>{{ s.house()?.name }}</strong
                ><small>Il nostro spazio</small>
              </div>
            </div>
          }
          <nav aria-label="Navigazione principale">
            @if (s.house() && !s.house()?.archived) {
              @for (link of links; track link.path) {
                <a
                  [routerLink]="link.path"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: true }"
                  ><span>{{ link.icon }}</span
                  >{{ link.label }}</a
                >
              }
            }
            @if (s.user()?.role === "ADMIN") {
              <a routerLink="/admin" routerLinkActive="active"
                ><span>⚙</span>Amministrazione</a
              >
            }
            <button (click)="s.refresh()" [disabled]="s.busy()">
              ↻ {{ s.mode() === "demo" ? "Ripristina anteprima" : "Aggiorna dati" }}
            </button>
          </nav>
          <div class="sidebar-bottom">
            <div class="member-row">
              <span class="avatar">
                @if (s.user()?.photo) {
                  <img [src]="s.user()?.photo" alt="Foto profilo" />
                } @else {
                  {{ s.user()?.name?.slice(0, 2) }}
                }</span
              ><strong>{{ s.user()?.name }}</strong>
            </div>
            <button (click)="s.logout()" [disabled]="s.busy()">
              {{ s.mode() === "demo" ? "Cambia profilo di esempio" : "Esci" }}
            </button>
            @if (s.mode() === "demo") {
              <small class="demo-badge"
                >Anteprima grafica · dati di esempio</small
              >
            }
          </div>
        </aside>
        <main class="main">
          <header class="topbar">
            <span
              >{{ s.house()?.emoji }} {{ s.house()?.name || "Benvenuto" }}</span
            ><span class="pill"
              >{{ s.activeMembers().length }} coinquilini</span
            >
          </header>
          @if (s.house()?.archived && !router.url.startsWith("/admin")) {
            <section class="panel">
              <h1>Casa archiviata</h1>
              <p>Chiedi all’amministratore di riattivarla.</p>
            </section>
          } @else if (s.house() || router.url.startsWith("/admin")) {
            <router-outlet />
          } @else {
            <cq-onboarding />
          }
        </main>
      </div>
    }
    @if (s.error()) {
      <div class="toast error" role="alert">
        <span>{{ s.error() }}</span
        ><button (click)="s.error.set('')" aria-label="Chiudi messaggio">
          ×
        </button>
      </div>
    }
    @if (s.message()) {
      <div class="toast" role="status">{{ s.message() }}</div>
    }
  </div>`,
})
export class App {
  s = inject(Store);
  router = inject(Router);
  links = [
    { path: "/", label: "Bacheca", icon: "▦" },
    { path: "/per-me", label: "Per me", icon: "☺" },
    { path: "/spese", label: "Spese", icon: "€" },
    { path: "/lista", label: "Lista", icon: "☷" },
    { path: "/mansioni", label: "Mansioni", icon: "✓" },
    { path: "/impostazioni", label: "Impostazioni", icon: "⚙" },
  ];
  constructor() {
    effect(() => applyTheme(this.s.user()?.color || "#3d59c9"));
  }
}

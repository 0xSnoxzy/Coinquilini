import { Component, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
import { User, House } from "../core/models";
@Component({
  selector: "cq-admin",
  standalone: true,
  imports: [FormsModule],
  template: `<div class="page-title">
      <div>
        <h1>Amministrazione.</h1>
        <p>Account e case della piattaforma.</p>
      </div>
    </div>
    @if (s.user()?.role === "ADMIN") {
      <section class="panel">
        <h2>Utenti</h2>
        @for (u of s.data()!.adminUsers; track u.id) {
          <div class="admin-row">
            <div class="grow">
              <strong>{{ u.name }}</strong
              ><small>{{ u.email }}</small>
            </div>
            <select
              [(ngModel)]="u.role"
              [disabled]="s.mine(u.id)"
              [attr.aria-label]="'Ruolo di ' + u.name"
            >
              <option value="USER">Utente</option>
              <option value="ADMIN">Amministratore</option></select
            ><label class="check"
              ><input
                type="checkbox"
                [(ngModel)]="u.active"
                [disabled]="s.mine(u.id)"
              />Attivo</label
            ><button
              [disabled]="s.mine(u.id) || s.busy()"
              (click)="saveUser(u)"
            >
              Salva
            </button>
          </div>
        }
      </section>
      <section class="panel top-gap">
        <h2>Case</h2>
        @for (h of s.data()!.adminHouses; track h.id) {
          <div class="row">
            <span class="emoji">{{ h.emoji }}</span>
            <div class="grow">
              <strong>{{ h.name }}</strong
              ><small>{{ h.archived ? "Archiviata" : "Attiva" }}</small>
            </div>
            <button [disabled]="s.busy()" (click)="toggleHouse(h)">
              {{ h.archived ? "Riattiva" : "Archivia" }}
            </button>
          </div>
        } @empty {
          <p class="empty">Nessuna casa registrata.</p>
        }
      </section>
    } @else {
      <section class="panel">
        Questa pagina è riservata agli amministratori.
      </section>
    }`,
})
export class Admin {
  s = inject(Store);
  saveUser(u: User) {
    void this.s.send("admin/user", {
      id: u.id,
      role: u.role,
      active: u.active,
    });
  }
  toggleHouse(h: House) {
    if (confirm((h.archived ? "Riattivare" : "Archiviare") + " questa casa?"))
      void this.s.send("admin/house", { id: h.id, archived: !h.archived });
  }
}

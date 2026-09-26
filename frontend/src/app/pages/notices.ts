import { Component, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
import { Modal } from "../shared/modal";
import { today } from "../core/dates";
import { Notice, Payload } from "../core/models";
// Responsabilità suggerita: Giovanna: avvisi della bacheca comune.
@Component({
  selector: "cq-notices",
  standalone: true,
  imports: [FormsModule, Modal],
  template: `<section class="panel">
      <div class="section-title">
        <h2>Da ricordare</h2>
        <button (click)="edit()">＋ Avviso</button>
      </div>
      @for (n of sorted(); track n.id) {
        <article class="notice">
          <div class="section-title">
            <span class="pill">{{ s.date(n.date) }} {{ n.time }}</span>
            @if (s.canEdit(n.authorId)) {
              <div class="actions">
                <button
                  class="icon-button"
                  (click)="edit(n)"
                  aria-label="Modifica avviso"
                >
                  ✎</button
                ><button
                  class="icon-button"
                  (click)="remove(n)"
                  aria-label="Elimina avviso"
                >
                  ×
                </button>
              </div>
            }
          </div>
          <h3>{{ n.title }}</h3>
          <p class="preserve-lines">{{ n.text }}</p>
          <small>{{ s.name(n.authorId) }} · Per tutta la casa</small>
        </article>
      } @empty {
        <p class="empty">
          Nessun avviso. Aggiungi il prossimo appuntamento della casa.
        </p>
      }
    </section>
    @if (draft) {
      <cq-modal
        [title]="draft['id'] ? 'Modifica avviso' : 'Nuovo avviso per la casa'"
        (closed)="draft = null"
        ><form #f="ngForm" (ngSubmit)="save()">
          <label
            >Titolo<input
              name="title"
              [(ngModel)]="draft['title']"
              required
              maxlength="100" /></label
          ><label
            >Messaggio<textarea
              name="text"
              [(ngModel)]="draft['text']"
              required
              maxlength="1000"
              rows="4"
            ></textarea>
          </label>
          <div class="form-grid">
            <label
              >Data<input
                type="date"
                name="date"
                [(ngModel)]="draft['date']"
                required /></label
            ><label
              >Ora (facoltativa)<input
                type="time"
                name="time"
                [(ngModel)]="draft['time']"
            /></label>
          </div>
          <p class="muted">Visibile a tutti i coinquilini della casa.</p>
          <button class="primary" [disabled]="f.invalid || s.busy()">
            Salva avviso
          </button>
        </form></cq-modal
      >
    }`,
})
export class Notices {
  s = inject(Store);
  draft: Payload | null = null;
  sorted() {
    return [...(this.s.data()?.notices || [])].sort((a, b) =>
      (a.date + a.time).localeCompare(b.date + b.time),
    );
  }
  edit(n?: Notice) {
    this.draft = n
      ? { ...n }
      : { title: "", text: "", date: today(), time: "" };
  }
  async save() {
    if (await this.s.send("notices/save", this.draft!)) this.draft = null;
  }
  remove(n: Notice) {
    if (confirm("Eliminare questo avviso?"))
      void this.s.send("notices/delete", { id: n.id });
  }
}

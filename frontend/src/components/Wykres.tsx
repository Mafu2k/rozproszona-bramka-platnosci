import { nazwaSieci } from '../lib/format';

interface Slupek {
  siec: string;
  wartosc: number;
  tekst: string;
}

/**
 * Poziomy wykres słupkowy do porównania sieci (kategorii).
 * Słupki są posortowane malejąco i opisane wprost (nazwa + wartość), więc kolor nie jest jedynym nośnikiem informacji.
 * Wykres zawsze towarzyszy tabeli z tymi samymi danymi.
 */
export function WykresSlupkowy({ tytul, slupki }: { tytul: string; slupki: Slupek[] }) {
  const max = Math.max(...slupki.map((s) => s.wartosc), 0);
  const posortowane = [...slupki].sort((a, b) => b.wartosc - a.wartosc);
  const opis = posortowane.map((s) => `${nazwaSieci(s.siec)}: ${s.tekst}`).join(', ');

  return (
    <figure className="wykres">
      <figcaption>{tytul}</figcaption>
      <div role="img" aria-label={`${tytul}. ${opis}`}>
        {posortowane.length === 0 && <div className="wyciszony maly">Brak danych</div>}
        {posortowane.map((s) => (
          <div key={s.siec} className="wykres-wiersz" aria-hidden>
            <span className="wykres-etykieta">{nazwaSieci(s.siec)}</span>
            <span className="wykres-tor">
              <span
                className={`wykres-slupek siec-tlo-${s.siec.toLowerCase()}`}
                style={{ width: max > 0 ? `${Math.max((s.wartosc / max) * 100, s.wartosc > 0 ? 1.5 : 0)}%` : 0 }}
              />
            </span>
            <span className="wykres-wartosc">{s.tekst}</span>
          </div>
        ))}
      </div>
    </figure>
  );
}

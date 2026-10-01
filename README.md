# Cirral

**Cirral** to aplikacja pogodowa na Androida. Pokazuje bieżące warunki, prognozę godzinową i dziesięciodniową oraz mapę ostatnich opadów. Działa bez konta, własnego serwera i klucza API.

## Co potrafi

- **Pogoda teraz:** temperatura, odczuwalna temperatura, opady, wiatr, wilgotność, indeks UV oraz wschód i zachód słońca.
- **Prognoza:** interaktywne wykresy na 24 godziny i widok 10 dni z temperaturą, opadami oraz porywami wiatru.
- **Radar opadów:** mapa z animacją historycznych klatek, wyborem czasu i wyśrodkowaniem na wybranej miejscowości.
- **Miejsca:** wyszukiwanie miast na świecie, lokalizacja telefonu i szybki powrót do ostatnio wybranych miejsc.
- **Ustawienia:** jednostki °C/°F i km/h/mph, pięć teł nieba, automatyczna zmiana tła oraz sprawdzanie aktualizacji.
- **Dostęp bez sieci:** ostatnia pobrana prognoza pozostaje widoczna wraz z czasem aktualizacji. Radar wymaga połączenia z internetem.

## Pobranie na telefon

Na telefonie wybierz **[Pobierz Cirral 1.1.0 (APK)](https://github.com/aimatthew/Cirral/releases/download/v1.1.0/Cirral-1.1.0.apk)**. Następnie otwórz pobrany plik i zezwól Androidowi na instalację z używanej przeglądarki, jeśli system o to poprosi. Wymagany jest Android 8.0 lub nowszy.

Wydania udostępniane bezpośrednio tutaj służą do instalacji poza Google Play. Źródłem pliku powinno być to repozytorium.

## Aktualizacje

Aplikacja sprawdza najnowsze opublikowane wydanie w tym repozytorium mniej więcej co 6 godzin, gdy telefon ma połączenie z internetem. Jeśli znajdzie nowszą wersję, pobiera plik APK do prywatnej pamięci aplikacji. W **Ustawieniach → Aktualizacje** można sprawdzić wersję i ostatni wynik, uruchomić sprawdzenie ręcznie oraz rozpocząć instalację. Android prosi o zgodę na instalację. Powiadomienia o pobranych aktualizacjach można osobno włączyć w ustawieniach aplikacji.

Pierwszą wersję z tym mechanizmem (`1.1.0`) trzeba zainstalować ręcznie, ponieważ wersja `1.0` nie umie jeszcze sprawdzać aktualizacji.

## Źródła danych i prywatność

Prognoza i wyszukiwanie miejsc korzystają z [Open-Meteo](https://open-meteo.com/), radar z [RainViewer](https://www.rainviewer.com/api/weather-maps-api.html), a mapa z [OpenFreeMap](https://openfreemap.org/) i MapLibre. Przy użyciu lokalizacji telefonu współrzędne są wysyłane do usługi pogodowej w celu pobrania prognozy. Ostatnie miejsca, ustawienia i prognoza są zapisywane lokalnie. Aplikacja nie ma własnego serwera ani kont użytkowników.

Radar pokazuje klatki **historyczne**, nie prognozę przyszłych opadów. Dostępność i szczegółowość danych zależą od zewnętrznych usług.

## Zasoby graficzne

Czcionka Poppins jest udostępniana na [licencji SIL Open Font License](design/Poppins-OFL.txt). Zdjęcie ekranu startowego: Leandro De Torres / [Unsplash](https://unsplash.com/photos/soft-clouds-in-a-pastel-sky-at-dusk-XWQa3P5c8WQ) ([licencja](https://unsplash.com/license)). Pozostałe tła nieba przygotowano dla projektu.

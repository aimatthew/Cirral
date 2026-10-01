# Wdrożenie zaakceptowanej makiety — 30.09.2026

## Wygląd

Fotograficzne niebo, szklane panele z rozmyciem samego tła, białe napisy Poppins, ciemne kapsułki, cieniowane ikony, pływająca nawigacja. Ekrany: Teraz, Prognoza, Radar oraz ustawienia. Tło pozostaje ilustracją; ikony i liczby opisują bieżące warunki. Radar pokazuje prawdziwe historyczne klatki, więc nie dodaje dekoracyjnego deszczu w pogodne dni.

Pięć fotograficznych teł nieba odpowiada porom dnia: ranek, południe, zachód, wieczór i noc. Tryb automatyczny porównuje lokalny czas wybranej miejscowości z godzinami wschodu i zachodu w prognozie; przy braku danych stosuje godziny zastępcze do czasu odświeżenia. Użytkownik może zatrzymać dowolne tło na stałe. Wybór i dodatkowe przyciemnienie są zapisywane lokalnie. Szklane panele używają tego samego obrazu co pełne tło.

Rozmycie Compose wymaga Androida 12. Na starszych wersjach pozostaje warstwa tinty; tekst nigdy nie jest rozmywany. Wszystkie prognozy są przewijane; panel radaru ogranicza wysokość i można go przewijać na niskim ekranie.

## Dane i interakcje

- Prawdziwe temperatury, wilgotność, wiatr, opady i wschód/zachód z Open-Meteo.
- Nowe pola: dzienny maksymalny UV oraz dzień/noc dla godzin. Starszy cache bez tych pól nadal się odczytuje.
- Ekran Teraz pokazuje maksimum i minimum temperatury bieżącego dnia. Godzinowa prognoza Open-Meteo dostarcza porywy wiatru na wysokości 10 m: widok 24 godzin pokazuje poryw dla wybranej godziny i skrajne porywy z godzinami, a widok 10 dni oblicza je osobno dla każdego dnia. Brak tego pola w starszym cache nie blokuje wyświetlenia prognozy.
- Każda karta prognozy 10 dni pokazuje datę w formacie `dd.MM` obok nazwy dnia.
- Wykresy temperatury i opadów korzystają ze wspólnego, płynnego przewijania poziomego przez 24 godziny. Dotknięcie punktu wybiera godzinę; podczas przewijania aktualizuje się godzina, temperatura i wiatr.
- Pole wyszukiwania znajduje miejscowości w Polsce po nazwie przez Open-Meteo Geocoding. Lista proponowanych miast znika podczas pisania.
- Przycisk lokalizacji pokazuje postęp i wynik, sprawdza włączoną usługę lokalizacji, używa bieżącego położenia lub świeżego ostatniego odczytu i daje drogę do ustawień przy błędzie.
- Lista miejsc, lokalizacja telefonu, jednostki i wygląd są pogrupowane w ustawieniach. Dolna nawigacja znika na tym ekranie, by zostawić więcej miejsca na ustawienia.
- Karta spaceru wskazuje godzinę z najmniejszym prawdopodobieństwem opadów wśród godzin 8–19 w najbliższej dobie; przy remisie porównuje sumę opadów i wiatr.
- Radar zachowuje odtwarzanie, ręczny wybór klatki, odświeżanie, wyśrodkowanie oraz informacje o źródłach i starych danych.
- Mapa radaru ma granatową paletę i szerszy początkowy widok dopasowany do rozdzielczości kafelków RainViewer. Ciemniejszy panel odcina się od mapy. Niejasny przełącznik warstw zastąpiono przyciskiem wyśrodkowania; nakładka opadów pozostaje zawsze włączona. Legenda odpowiada palecie Universal Blue.

## Weryfikacja

- `:app:assembleDebug` — zakończone sukcesem.
- Instalacja i uruchomienie na Pixel 9a, emulator Android 16 / API 36, 1080×2424, gęstość 420.
- Sprawdzone wizualnie: Teraz, Prognoza, Radar, lista miejsc.
- Sprawdzony wybór Warszawy i wyszukanego Krakowa, przełącznik Godziny/Dni, ikony księżyca po zachodzie, płynne przesuwanie obu wykresów w przód i wstecz, widok 10 dni, odświeżanie danych i UV.
- Sprawdzone na emulatorze: maksimum i minimum temperatury na ekranie Teraz, poryw wybranej godziny, skrajne porywy w 24 godzinach oraz skrajne porywy z godzinami na kartach dni.
- Sprawdzone na emulatorze: ręczny wybór tła południowego i nocnego, kontrast ekranu Teraz, podgląd automatycznego zachodu, zapis wyboru po ponownym uruchomieniu i powrót do trybu automatycznego.
- Odtwarzanie i pauza radaru: wybrany czas przesunął się z 17:40 do 16:00 po rozpoczęciu cyklu i zatrzymaniu. Oznaczenia źródeł pozostają widoczne.
- Log AndroidRuntime nie zawierał błędów podczas sprawdzenia.
- Zrzuty rzeczywistego interfejsu: `design/screenshots/`. Dane pogodowe są zmienne.
- Nie testowano na fizycznym telefonie ani na Androidzie 8–11.

## Zasoby

Poppins Regular / SemiBold pochodzą z oficjalnego repozytorium Google Fonts: https://github.com/google/fonts/tree/main/ofl/poppins. Licencja SIL OFL znajduje się w `design/Poppins-OFL.txt` oraz w zasobach pakietu APK (`app/src/main/assets/fonts/`).

Tła w `app/src/main/res/drawable-nodpi/`: `sky_morning.png`, `sky_noon.png`, `sky_reference_v2.png` (zachód), `sky_evening.png` i `sky_night.png`. Powstały narzędziem image_gen na podstawie obrazu nieba dostarczonego przez użytkownika. Wcześniejszy plik `sky_background.png` pozostaje w projekcie jako nieużywany wariant.

Ekran startowy Cirral używa osobnego zdjęcia naturalnych chmur: `app/src/main/res/drawable-nodpi/launch_atmosphere.jpg`. Autor: Leandro De Torres, [Unsplash](https://unsplash.com/photos/soft-clouds-in-a-pastel-sky-at-dusk-XWQa3P5c8WQ), [licencja Unsplash](https://unsplash.com/license). Nie jest to tło głównego interfejsu. Znak C jest wektorowy i wspólny dla ikony aplikacji oraz ekranu startowego.

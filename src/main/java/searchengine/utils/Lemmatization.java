package searchengine.utils;

import lombok.SneakyThrows;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Component
public class Lemmatization {
    private final RussianLuceneMorphology russianLuceneMorphology = new RussianLuceneMorphology();
    private final EnglishLuceneMorphology englishLuceneMorphology = new EnglishLuceneMorphology();
    private static final String[] particlesRus = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private static final String[] particlesEng = new String[]{"PN", "PREP", "PART", "ARTICLE"};

    public Lemmatization() throws IOException {
    }

    @SneakyThrows
    public Map<String, Integer> getLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();

        List<String> rusWords = splitRusText(text);
        for (String word : rusWords) {
            try {
                if (word.isBlank()) continue;

                if (isNotRusWord(word)) continue;

                List<String> normalForms = russianLuceneMorphology.getNormalForms(word.toLowerCase());
                if (normalForms.isEmpty()) continue;

                String normalWord = normalForms.get(0);

                if (lemmas.containsKey(normalWord)) {
                    lemmas.put(normalWord, lemmas.get(normalWord) + 1);
                } else {
                    lemmas.put(normalWord, 1);
                }
            } catch (Exception e) {
                System.out.println("Can't get lemmas from word: " + word);
            }
        }

        List<String> engWords = splitEngText(text);
        for (String word : engWords) {
            try {
                if (word.isBlank()) continue;

                if (isNotEngWord(word)) continue;

                List<String> normalForms = englishLuceneMorphology.getNormalForms(word.toLowerCase());
                if (normalForms.isEmpty()) continue;

                String normalWord = normalForms.get(0);

                if (lemmas.containsKey(normalWord)) {
                    lemmas.put(normalWord, lemmas.get(normalWord) + 1);
                } else {
                    lemmas.put(normalWord, 1);
                }
            } catch (Exception e) {
                System.out.println("Can't get lemmas from word: " + word);
            }
        }
        return lemmas;
    }

    private List<String> splitRusText(String text) {
        String[] words = text.toLowerCase()
                .replaceAll("[^А-яЁё\\s]", "")
                .trim()
                .split("\\s+");
        return new ArrayList<>(List.of(words));
    }

    private List<String> splitEngText(String text) {
        String[] words = text.toLowerCase()
                .replaceAll("[^A-z\\s]", "")
                .trim()
                .split("\\s+");
        return new ArrayList<>(List.of(words));
    }

    private boolean isNotRusWord(String word) {
        List<String> rusWordInfo = russianLuceneMorphology.getMorphInfo(word);
        for (String property : particlesRus) {
            if (rusWordInfo.toString().toUpperCase().contains(property)) {
                return russianLuceneMorphology.checkString(word);
            }
        }
        return false;
    }

    private boolean isNotEngWord(String word) {
        List<String> engWordInfo = englishLuceneMorphology.getMorphInfo(word);
        for (String property : particlesEng) {
            if (engWordInfo.toString().toUpperCase().contains(property)) {
                return englishLuceneMorphology.checkString(word);
            }
        }
        return false;
    }
}
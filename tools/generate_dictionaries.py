#!/usr/bin/env python3
"""Builds app/src/main/assets/dict/<lang>.txt (one word per line, most frequent first)
from the 'wordfreq' package (pip install wordfreq). See assets/dict/NOTICE.txt for the data licence."""
import os, re
from wordfreq import top_n_list

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'assets', 'dict')
LIMITS = {'en': 40000, 'fr': 25000, 'de': 25000, 'es': 25000}
WORD = re.compile(r"^[^\W\d_]+(?:'[^\W\d_]+)*$")
# Never offered as suggestions (typing them yourself still works). Stems match any ending.
BLOCKED_STEMS = ('fuck', 'shit', 'cunt', 'nigg', 'fagg', 'bitch', 'whore', 'slut', 'porn', 'retard',
                 'motherf', 'asshole', 'blowjob', 'dildo', 'wank')
BLOCKED_WORDS = {'ass', 'cum', 'fag', 'tits', 'cock', 'cocks', 'dick', 'dicks', 'pussy', 'rape', 'raped',
                 'puta', 'putas', 'puto', 'mierda', 'joder', 'putain', 'merde', 'salope', 'fotze', 'hure'}

# English is also checked against the SCOWL spelling lists (apt install wamerican-large wbritish-large),
# because frequency data from the web contains common misspellings.
CHAT = {'ok', 'okay', 'lol', 'omg', 'btw', 'gonna', 'wanna', 'gotta', 'yeah', 'yep', 'nope', 'haha', 'hmm', 'pls', 'plz',
        'thx', 'app', 'apps', 'emoji', 'selfie', 'google', 'whatsapp', 'instagram', 'telegram', 'youtube', 'wifi', 'online'}


def spelling_words():
    found = set(CHAT)
    for name in ('american-english-large', 'british-english-large'):
        with open('/usr/share/dict/' + name, encoding='utf-8') as f:
            found.update(line.strip().lower() for line in f)
    return found


CHECK = spelling_words()
os.makedirs(OUT, exist_ok=True)
for lang, limit in LIMITS.items():
    words = []
    for w in top_n_list(lang, limit * 2):
        w = w.replace('’', "'")
        if not WORD.match(w) or len(w) > 24 or w in BLOCKED_WORDS or any(s in w for s in BLOCKED_STEMS):
            continue
        if lang == 'en' and w not in CHECK:
            continue
        if len(w) == 1 and not (lang == 'en' and w in 'ai'):
            continue
        words.append(w)
        if len(words) == limit:
            break
    with open(os.path.join(OUT, lang + '.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(words) + '\n')
    print(lang, len(words))

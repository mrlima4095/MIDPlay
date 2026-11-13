import re

p = 'src/midplay/util/Lang.java'
s = open(p).read()

keys = ['web.title','web.host','web.port','web.user','web.password','web.device',
        'web.connect','web.disconnect','web.forget','web.connecting',
        'web.disconnected','web.forgotten','web.host_empty','web.user_empty']

en = ['MIDPlay Web','Server','Port','User','Password','Device name',
      'Connect','Disconnect','Forget credentials','Connecting...',
      'Disconnected','Credentials forgotten','Enter the server','Enter the user']
pt = ['MIDPlay Web','Servidor','Porta','Usuario','Senha','Nome do aparelho',
      'Conectar','Desconectar','Esquecer credenciais','Conectando...',
      'Desconectado','Credenciais esquecidas','Informe o servidor','Informe o usuario']

def q(items, indent):
    return ',\n' + indent + ', '.join('"%s"' % x for x in items)

# 1) KEYS termina com "loop.error.span"
old_keys = '"loop.error.span"\n  };'
assert s.count(old_keys) == 1, 'anchor KEYS'
s = s.replace(old_keys, '"loop.error.span"' + q(keys, '    ') + '\n  };', 1)

# 2) VALUES: ordem no arquivo EN, HE, PL, PT, TR, VI
en_anchor = '"Point B must be at least 0.5s after A"\n    };'
pt_anchor = '"O ponto B precisa estar pelo menos 0,5s depois de A"\n    };'

assert s.count(en_anchor) == 5, 'en anchors: %d' % s.count(en_anchor)
assert s.count(pt_anchor) == 1, 'pt anchors: %d' % s.count(pt_anchor)

# substitui os 5 em ingles por en
s = s.replace(en_anchor, '"Point B must be at least 0.5s after A"' + q(en, '      ') + '\n    };')
# substitui o pt
s = s.replace(pt_anchor, '"O ponto B precisa estar pelo menos 0,5s depois de A"' + q(pt, '      ') + '\n    };')

open(p, 'w').write(s)

m = re.search(r'KEYS = \{(.*?)\};', s, re.S)
print('KEYS:', m.group(1).count('"') // 2)
for blk in ['EN','HE','PL','PT','TR','VI']:
    mm = re.search(blk + r' \{\s*static final String\[\] VALUES = \{(.*?)\};', s, re.S)
    print(blk, mm.group(1).count('"') // 2)
print('web.title:', s.count('web.title'))

with open('build.gradle.kts', 'r') as f:
    content = f.read()
content = content.replace('hilt.android" version "2.50"', 'hilt.android" version "2.51.1"')
with open('build.gradle.kts', 'w') as f:
    f.write(content)
print("Done:", content[content.find('hilt'):content.find('hilt')+60])

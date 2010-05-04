import re

Assocs = [1, 2, 4, 8, 16]
Blocks = [4, 8, 16,32,64,128,256,512]
Capacity = [4,8,16,32,64]

container = []

for assoc in Assocs:
    assoclist = []
    for block in Blocks:
        for capac in Capacity:
            folderString = 'Assoc' + str(assoc) + 'traces/'
            fileString = 'b' + str(block) + '-c' + str(capac) + '.out'
            currentString = folderString + fileString
            currentFile = open( currentString, 'r')
            lines = currentFile.readlines()
            #numberOmisses = lines[2]
            if len(lines) > 2:
                rateOmisses = lines[4]
                searched = re.search('[0-9].[0-9]*', rateOmisses)
                if not( searched is None ):
                    assoclist.append(searched.groups(0))
                else:
                    assoclist.append(0)
    container.append(assoclist)

#Now report
report = "Assoc. "
for block in Blocks:
    for capac in Capacity:
        report += " " + str(block) + " / " + str(capac) + " "
print report

for assoc in Assocs:
    thisline = str(assoc) + "   "
    i = 0
    for block in Blocks:
        for capac in Capacity:
            thisline += str(container[assoc][i]) + "  "
            i += 1


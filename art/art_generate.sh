#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
mkdir -p "$DIR/framed/"

generate () {
    SCREENSHOT1="$1"
    OUTPUT_FILE="$DIR/framed/$(basename $1)"
    FRAMEFILE="$DIR/moto360.png"
    FRAMEFILE1_SCREEN_GEOMETRY="275x275"
    FRAMEFILE1_TOP_LEFT_OFFSET="+118+120"
    convert "${FRAMEFILE}" \
        \( "${SCREENSHOT1}" -scale ${FRAMEFILE1_SCREEN_GEOMETRY} \) -geometry ${FRAMEFILE1_TOP_LEFT_OFFSET} -compose SrcOver -composite \
        "${OUTPUT_FILE}"
    echo "Created $OUTPUT_FILE"
}

for var in "$@"
do 
    echo "doing $var"
    generate "$var"
done

echo "done"
#!/usr/bin/env python3

import csv
import sys
import xlsxwriter


def read_data(filename):
    with open(filename, 'r') as f:
        reader = csv.reader(f, delimiter=',')
        return list(reader)[1:]


def create_worksheet(workbook, worksheet_name, data):
    print('Processing ' + worksheet_name)

    if worksheet_name.startswith('stats-'):
        worksheet_name = worksheet_name[len('stats-'):]
    if worksheet_name.endswith('.csv'):
        worksheet_name = worksheet_name[:-len('.csv')]

    worksheet_name = worksheet_name[:30]

    worksheet = workbook.add_worksheet(worksheet_name)
    headers = ('File', 'ast', 'ast %', 'walk', 'walk %', 'append comments', 'append comments %',
               'walk comments', 'walk comments %', 'total', 'file size (bytes)')

    green_bg_format = workbook.add_format({'bg_color': '#00ff00'})
    yellow_bg_format = workbook.add_format({'bg_color': '#ffff00'})
    red_bg_format = workbook.add_format({'bg_color': '#ff0000'})
    black_bg_format = workbook.add_format({'bg_color': '#000000', 'font_color': '#ffffff'})
    percent_format = workbook.add_format({'num_format': '0.00%'})
    two_decimal_format = workbook.add_format({'num_format': '0.00'})

    for i, h in enumerate(headers):
        worksheet.write_string(0, i, h)

    total_column = 9
    total_column_chr = chr(ord('A') + total_column)
    file_size_data_column = 6
    sorted_data = sorted((x for x in data if x), key=lambda x: int(x[file_size_data_column]), reverse=True)

    for row, row_data in enumerate(sorted_data, 1):
        if not row_data:
            continue
        file, ast, walk, append_comments, walk_comments, total, file_size = row_data

        row_str = str(row + 1)
        total_cell = total_column_chr + row_str
        worksheet.write_string(row, 0, file)
        worksheet.write_number(row, 1, int(ast))
        worksheet.write(row, 2, '=B' + row_str + '/' + total_cell, percent_format)
        worksheet.write_number(row, 3, int(walk))
        worksheet.write(row, 4, '=D' + row_str + '/' + total_cell, percent_format)
        worksheet.write_number(row, 5, int(append_comments))
        worksheet.write(row, 6, '=F' + row_str + '/' + total_cell, percent_format)
        worksheet.write_number(row, 7, int(walk_comments))
        worksheet.write(row, 8, '=H' + row_str + '/' + total_cell, percent_format)
        worksheet.write_number(row, 9, int(total))
        worksheet.write_number(row, 10, int(file_size))

    colored_format_columns = ('C', 'E', 'G', 'I')
    for c in colored_format_columns:
        range = '{0}2:{0}{1}'.format(c, len(data))
        formats = [
            (0.75, black_bg_format),
            (0.5, red_bg_format),
            (0.25, yellow_bg_format),
            (0, green_bg_format),
        ]
        for v, f in formats:
            worksheet.conditional_format(range,
                                         {'type': 'cell',
                                          'criteria': 'greater than',
                                          'value': v,
                                          'format': f})

    stats_column = len(headers) + 2
    stats_column_str = chr(ord('A') + stats_column)
    stats_results_column_str = chr(ord('A') + stats_column + 1)

    worksheet.write_string(0, stats_column, 'Average AST time')
    worksheet.write(0, stats_column + 1, '=AVERAGE(B1:B' + str(len(sorted_data)) + ')')

    worksheet.write_string(1, stats_column, 'AST creation')
    worksheet.write(1, stats_column + 1, '=AVERAGE(C1:C' + str(len(sorted_data)) + ')', percent_format)

    worksheet.write_string(2, stats_column, 'Walk')
    worksheet.write(2, stats_column + 1, '=AVERAGE(E1:E' + str(len(sorted_data)) + ')', percent_format)

    worksheet.write_string(3, stats_column, 'Append Comments')
    worksheet.write(3, stats_column + 1, '=AVERAGE(G1:G' + str(len(sorted_data)) + ')', percent_format)

    worksheet.write_string(4, stats_column, 'Walk Comments')
    worksheet.write(4, stats_column + 1, '=AVERAGE(I1:I' + str(len(sorted_data)) + ')', percent_format)

    worksheet.write_string(5, stats_column, 'Total')
    worksheet.write(5, stats_column + 1, '=SUM(J2:J' + str(len(sorted_data)) + ') / 1000 / 1000', two_decimal_format)
    worksheet.write(5, stats_column + 2, 'ms')

    chart = workbook.add_chart({'type': 'pie'})

    chart.add_series({
        'categories': '={0}!${1}$2:${1}$5'.format(worksheet_name, stats_column_str),
        'values': '={0}!${1}$2:${1}$5'.format(worksheet_name, stats_results_column_str),
    })

    worksheet.insert_chart(stats_column_str + '8', chart)

    return worksheet_name


def create_total_chart(workbook, worksheet_names):
    worksheet_name = 'Total'
    worksheet = workbook.add_worksheet(worksheet_name)
    chart = workbook.add_chart({'type': 'pie'})

    percent_format = workbook.add_format({'num_format': '0.00%'})

    for i, name in enumerate(worksheet_names):
        worksheet.write(i, 0, name)
        worksheet.write(i, 1, '={0}!$O$6'.format(name))
        worksheet.write(i, 2, '={0}!$O$6 / $B${1}'.format(name, len(worksheet_names) + 3), percent_format)

    worksheet.write(len(worksheet_names) + 2, 0, 'Total')
    worksheet.write(len(worksheet_names) + 2, 1, '=SUM($B$1:$B${0})'.format(len(worksheet_names)))

    chart.add_series({
        'categories': '=Total!$A$1:$A${0}'.format(len(worksheet_names)),
        'values': '=Total!$B$1:$B${0}'.format(len(worksheet_names))
    })

    worksheet.insert_chart('A1', chart)


def create_workbook(filename, data):
    workbook = xlsxwriter.Workbook(filename)
    worksheet_names = []
    for name, sheet_data in data:
        worksheet_names.append(create_worksheet(workbook, name, sheet_data))
    create_total_chart(workbook, worksheet_names)

    workbook.close()


def main():
    if len(sys.argv) < 2:
        print('Usage: {} <csv file to prettify>'.format(sys.argv[0]))
        sys.exit(1)

    data = [(filename, read_data(filename)) for filename in sys.argv[1:]]
    create_workbook('stats.xlsx', data)


if __name__ == '__main__':
    main()

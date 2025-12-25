// Утилита для парсинга CSV файла с данными по регионам

export const parseCSV = (csvText) => {
  const lines = csvText.split('\n').filter(line => line.trim());
  const dataLines = lines.slice(2);
  
  const diseaseNames = [
    'Врожденные аномалии (пороки развития), деформации и хромосомные нарушения',
    'Врожденные аномалии системы кровообращения',
    'Врожденные аномалии развития нервной системы',
    'Врожденные деформации бедра',
    'Неопределенность пола и псевдогермафродитизм',
    'Врожденный ихтиоз'
  ];
  
  const regionsData = {};
  
  dataLines.forEach(line => {
    const parts = line.split(';').map(p => p.trim());
    if (parts.length < 2) return;
    
    const regionName = parts[0];
    if (!regionName || regionName.includes('ФО') || regionName === 'Российская Федерация') {
      return;
    }
    
    const statistics = {};
    let totalCases = 0;
    
    for (let i = 0; i < diseaseNames.length; i++) {
      const absoluteIndex = 2 + i * 2;
      if (absoluteIndex < parts.length) {
        const absoluteStr = parts[absoluteIndex] || '0';
        const absolute = parseFloat(absoluteStr.replace(',', '.')) || 0;
        const rateIndex = absoluteIndex + 1;
        const rateStr = parts[rateIndex] || '0';
        const rate = parseFloat(rateStr.replace(',', '.')) || 0;
        
        statistics[diseaseNames[i]] = {
          cases: Math.round(absolute),
          rate: rate
        };
        totalCases += Math.round(absolute);
      }
    }
    
    if (Object.keys(statistics).length > 0) {
      regionsData[regionName] = {
        name: regionName,
        statistics: statistics,
        totalCases: totalCases
      };
    }
  });
  
  return regionsData;
};

export const regionNameMapping = {
  'Республика Карелия': 'Karelia',
  'Республика Коми': 'Komi',
  'Архангельская обл. без АО': 'Arkhangelsk',
  'Ненецкий автономный округ': 'Nenets',
  'Вологодская область': 'Vologda',
  'Калининградская область': 'Kaliningrad',
  'Ленинградская область': 'Leningrad',
  'Мурманская область': 'Murmansk',
  'Новгородская область': 'Novgorod',
  'Псковская область': 'Pskov',
  'г. Санкт-Петербург': 'CityofSt.Petersburg',
};

export const mapRegionNameToKey = (russianName) => {
  return regionNameMapping[russianName] || russianName;
};

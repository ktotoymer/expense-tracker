import React, { useState, useRef, useEffect, useMemo } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import * as d3 from 'd3';
import { parseCSV, mapRegionNameToKey } from './utils/csvParser';

// Соответствие английских названий регионов русским
const regionNames = {
  'Adygey': 'Адыгея',
  'Altay': 'Алтайский край',
  'Amur': 'Амурская область',
  'Astrakhan': 'Астраханская область',
  'Bashkortostan': 'Башкортостан',
  'Belgorod': 'Белгородская область',
  'Bryansk': 'Брянская область',
  'Buryat': 'Бурятия',
  'Chechnya': 'Чечня',
  'Chelyabinsk': 'Челябинская область',
  'Chukot': 'Чукотский АО',
  'Chuvash': 'Чувашия',
  'CityofSt.Petersburg': 'Санкт-Петербург',
  'Dagestan': 'Дагестан',
  'Gorno-Altay': 'Республика Алтай',
  'Ingush': 'Ингушетия',
  'Irkutsk': 'Иркутская область',
  'Ivanovo': 'Ивановская область',
  'Kabardin-Balkar': 'Кабардино-Балкария',
  'Kaliningrad': 'Калининградская область',
  'Kalmyk': 'Калмыкия',
  'Kaluga': 'Калужская область',
  'Kamchatka': 'Камчатский край',
  'Karachay-Cherkess': 'Карачаево-Черкесия',
  'Karelia': 'Карелия',
  'Kemerovo': 'Кемеровская область',
  'Khabarovsk': 'Хабаровский край',
  'Khakass': 'Хакасия',
  'Khanty-Mansiy': 'ХМАО',
  'Kirov': 'Кировская область',
  'Komi': 'Коми',
  'Kostroma': 'Костромская область',
  'Krasnodar': 'Краснодарский край',
  'Krasnoyarsk': 'Красноярский край',
  'Kurgan': 'Курганская область',
  'Kursk': 'Курская область',
  'Leningrad': 'Ленинградская область',
  'Lipetsk': 'Липецкая область',
  'Magadan': 'Магаданская область',
  'Mariy-El': 'Марий Эл',
  'Mordovia': 'Мордовия',
  'MoscowCity': 'Москва',
  'Moskva': 'Московская область',
  'Murmansk': 'Мурманская область',
  'Nenets': 'Ненецкий АО',
  'Nizhegorod': 'Нижегородская область',
  'NorthOssetia': 'Северная Осетия',
  'Novgorod': 'Новгородская область',
  'Novosibirsk': 'Новосибирская область',
  'Omsk': 'Омская область',
  'Orel': 'Орловская область',
  'Orenburg': 'Оренбургская область',
  'Penza': 'Пензенская область',
  'Perm\'': 'Пермский край',
  'Primor\'ye': 'Приморский край',
  'Pskov': 'Псковская область',
  'Rostov': 'Ростовская область',
  'Ryazan\'': 'Рязанская область',
  'Sakha': 'Якутия',
  'Sakhalin': 'Сахалинская область',
  'Samara': 'Самарская область',
  'Saratov': 'Саратовская область',
  'Smolensk': 'Смоленская область',
  'Stavropol\'': 'Ставропольский край',
  'Sverdlovsk': 'Свердловская область',
  'Tambov': 'Тамбовская область',
  'Tatarstan': 'Татарстан',
  'Tomsk': 'Томская область',
  'Tula': 'Тульская область',
  'Tuva': 'Тува',
  'Tver': 'Тверская область',
  'Tyumen': 'Тюменская область',
  'Udmurt': 'Удмуртия',
  'Ulyanovsk': 'Ульяновская область',
  'Vladimir': 'Владимирская область',
  'Volgograd': 'Волгоградская область',
  'Vologda': 'Вологодская область',
  'Voronezh': 'Воронежская область',
  'Yamal-Nenets': 'ЯНАО',
  'Yaroslavl': 'Ярославская область',
  'Yevrey': 'Еврейская АО',
  'Zabaykalye': 'Забайкальский край',
  'Arkhangelsk': 'Архангельская область',
  'Arkhangel\'sk': 'Архангельская область',
};

// Функция для получения цвета региона (градиент от зеленого к красному)
const getRegionColor = (totalCases, minCases = 0, maxCases = 40000) => {
  if (!totalCases || totalCases === 0) return '#dee2e6'; // Серый для регионов без данных
  
  // Нормализуем значение от 0 до 1
  const normalized = Math.min(Math.max((totalCases - minCases) / (maxCases - minCases), 0), 1);
  
  // Вычисляем RGB значения для градиента зеленый -> желтый -> красный
  let r, g, b;
  
  if (normalized < 0.5) {
    // От зеленого к желтому (0.0 -> 0.5)
    const t = normalized * 2; // 0.0 -> 1.0
    r = Math.round(51 + (255 - 51) * t); // 51 -> 255
    g = 255;
    b = Math.round(51 * (1 - t)); // 51 -> 0
  } else {
    // От желтого к красному (0.5 -> 1.0)
    const t = (normalized - 0.5) * 2; // 0.0 -> 1.0
    r = 255;
    g = Math.round(255 * (1 - t)); // 255 -> 0
    b = 0;
  }
  
  return `rgb(${r}, ${g}, ${b})`;
};

// Компонент карты России
const RussiaMap = ({ onRegionClick, selectedRegion, geoData, regionsData, minCases, maxCases }) => {
  const [transform, setTransform] = useState({ x: 0, y: 0, scale: 1 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [hoveredRegion, setHoveredRegion] = useState(null);
  const containerRef = useRef(null);

  const handleWheel = (e) => {
    e.preventDefault();
    const rect = containerRef.current.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;
    
    const delta = e.deltaY * -0.001;
    const newScale = Math.min(Math.max(0.5, transform.scale + delta * transform.scale), 5);
    
    const scaleRatio = newScale / transform.scale;
    const newX = mouseX - (mouseX - transform.x) * scaleRatio;
    const newY = mouseY - (mouseY - transform.y) * scaleRatio;
    
    setTransform({ x: newX, y: newY, scale: newScale });
  };

  const handleMouseDown = (e) => {
    if (e.target.tagName === 'path') return;
    setIsDragging(true);
    setDragStart({ x: e.clientX - transform.x, y: e.clientY - transform.y });
  };

  const handleMouseMove = (e) => {
    if (!isDragging) return;
    setTransform(prev => ({
      ...prev,
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y
    }));
  };

  const handleMouseUp = () => setIsDragging(false);

  useEffect(() => {
    const container = containerRef.current;
    if (container) {
      container.addEventListener('wheel', handleWheel, { passive: false });
      return () => container.removeEventListener('wheel', handleWheel);
    }
  }, [transform.scale, transform.x, transform.y]);

  const paths = useMemo(() => {
    if (!geoData?.features) return null;

    const width = 1200;
    const height = 800;
    
    const projection = d3.geoMercator()
      .center([100, 65])
      .scale(600)
      .translate([width / 2, height / 2]);
    
    const pathGenerator = d3.geoPath().projection(projection);

    return geoData.features.map((feature, index) => {
      const regionKey = feature.properties.NAME_1;
      const pathData = pathGenerator(feature);
      const centroid = pathGenerator.centroid(feature);
      const data = regionsData[regionKey];
      const totalCases = data ? Object.values(data.statistics || {}).reduce((sum, item) => sum + (item.cases || 0), 0) : 0;

      return { index, regionKey, pathData, centroid, data, totalCases };
    });
  }, [geoData, regionsData]);

  if (!paths) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#868e96' }}>
        <div style={{ textAlign: 'center' }}>
          <h2>Загрузите GeoJSON файл карты России</h2>
          <p>Используйте кнопку "Загрузить GeoJSON" выше</p>
        </div>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: '100%', cursor: isDragging ? 'grabbing' : 'grab', overflow: 'hidden', position: 'relative' }}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
    >
      <svg width="100%" height="100%" viewBox="0 0 1200 800">
        <rect width="1200" height="800" fill="#e9ecef" />
        <g transform={`translate(${transform.x}, ${transform.y}) scale(${transform.scale})`}>
          {paths.map(({ index, regionKey, pathData, centroid, data, totalCases }) => {
            const isSelected = selectedRegion === regionKey;
            const isHovered = hoveredRegion === regionKey;
            const color = getRegionColor(totalCases, minCases, maxCases);

            return (
              <g key={index}>
                <path
                  d={pathData}
                  fill={color}
                  stroke="#fff"
                  strokeWidth={isSelected ? 2 : 1}
                  opacity={isSelected ? 1 : isHovered ? 0.9 : 0.8}
                  style={{ cursor: 'pointer', transition: 'opacity 0.2s ease' }}
                  onClick={() => onRegionClick(regionKey)}
                  onMouseEnter={() => setHoveredRegion(regionKey)}
                  onMouseLeave={() => setHoveredRegion(null)}
                />
                {isSelected && centroid && !isNaN(centroid[0]) && (
                  <text
                    x={centroid[0]}
                    y={centroid[1]}
                    textAnchor="middle"
                    fill="#000"
                    fontSize="12"
                    fontWeight="bold"
                    pointerEvents="none"
                    style={{ userSelect: 'none', textShadow: '1px 1px 2px white, -1px -1px 2px white' }}
                  >
                    {regionNames[regionKey] || regionKey}
                  </text>
                )}
              </g>
            );
          })}
        </g>
      </svg>
      {hoveredRegion && (
        <div style={{
          position: 'absolute',
          bottom: '20px',
          left: '50%',
          transform: 'translateX(-50%)',
          backgroundColor: 'rgba(0,0,0,0.85)',
          color: '#fff',
          padding: '8px 16px',
          borderRadius: '4px',
          fontSize: '14px',
          pointerEvents: 'none',
          zIndex: 1000
        }}>
          {regionNames[hoveredRegion] || hoveredRegion}
        </div>
      )}
    </div>
  );
};

// Основной компонент
const App = () => {
  const [selectedRegion, setSelectedRegion] = useState(null);
  const [showPanel, setShowPanel] = useState(false);
  const [geoData, setGeoData] = useState(null);
  const [regionsData, setRegionsData] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [minCases, setMinCases] = useState(0);
  const [maxCases, setMaxCases] = useState(40000);

  // Загрузка CSV данных при монтировании компонента
  useEffect(() => {
    fetch('/data.csv')
      .then(res => res.text())
      .then(csvText => {
        const parsedData = parseCSV(csvText);
        
        // Преобразуем русские названия в ключи для карты
        const mappedData = {};
        Object.entries(parsedData).forEach(([russianName, data]) => {
          const key = mapRegionNameToKey(russianName);
          mappedData[key] = data;
        });
        
        setRegionsData(mappedData);
        
        // Вычисляем min и max для нормализации цветов
        const allCases = Object.values(mappedData).map(region => 
          Object.values(region.statistics || {}).reduce((sum, item) => sum + (item.cases || 0), 0)
        ).filter(cases => cases > 0);
        
        if (allCases.length > 0) {
          setMinCases(Math.min(...allCases));
          setMaxCases(Math.max(...allCases));
        }
      })
      .catch(err => {
        console.error('Ошибка загрузки CSV:', err);
        setError('Не удалось загрузить данные из CSV файла');
      });
  }, []);

  useEffect(() => {
    setLoading(true);
    fetch('/gadm41_RUS_1.json')
      .then(res => {
        if (!res.ok) throw new Error('Ошибка загрузки GeoJSON');
        return res.json();
      })
      .then(data => { setGeoData(data); setLoading(false); })
      .catch(err => { setError('Не удалось загрузить карту России'); setLoading(false); });
  }, []);

  const handleRegionClick = (regionKey) => {
    setSelectedRegion(regionKey);
    setShowPanel(true);
  };

  const handleFileUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setLoading(true); setError(null);
    const reader = new FileReader();
    reader.onload = (e) => {
      try { const json = JSON.parse(e.target.result); setGeoData(json); setLoading(false); }
      catch { setError('Ошибка при чтении файла.'); setLoading(false); }
    };
    reader.readAsText(file);
  };

  const handleCSVUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setLoading(true);
    setError(null);
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const parsedData = parseCSV(e.target.result);
        const mappedData = {};
        Object.entries(parsedData).forEach(([russianName, data]) => {
          const key = mapRegionNameToKey(russianName);
          mappedData[key] = data;
        });
        setRegionsData(mappedData);
        
        const allCases = Object.values(mappedData).map(region => 
          Object.values(region.statistics || {}).reduce((sum, item) => sum + (item.cases || 0), 0)
        ).filter(cases => cases > 0);
        
        if (allCases.length > 0) {
          setMinCases(Math.min(...allCases));
          setMaxCases(Math.max(...allCases));
        }
        setLoading(false);
      } catch (err) {
        setError('Ошибка при чтении CSV файла.');
        setLoading(false);
      }
    };
    reader.readAsText(file);
  };

  const regionData = selectedRegion ? regionsData[selectedRegion] : null;
  const regionDisplayName = selectedRegion ? regionNames[selectedRegion] : '';

  return (
    <div style={{ display: 'flex', height: '100vh', fontFamily: 'Arial, sans-serif' }}>
      {/* Панель */}
      <div style={{
        width: showPanel ? '400px' : '0',
        backgroundColor: '#fff',
        boxShadow: '2px 0 10px rgba(0,0,0,0.1)',
        transition: 'width 0.3s ease',
        overflow: 'hidden',
        zIndex: 10
      }}>
        {showPanel && regionData && (
          <div style={{ padding: '20px', height: '100%', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2 style={{ margin: 0, color: '#212529', fontSize: '20px' }}>{regionDisplayName}</h2>
              <button onClick={() => setShowPanel(false)} style={{ background: 'none', border: 'none', fontSize: '24px', cursor: 'pointer', color: '#868e96' }}>×</button>
            </div>

            {/* Блок с общим числом случаев */}
            <div style={{ 
              backgroundColor: getRegionColor(Object.values(regionData.statistics).reduce((sum, item) => sum + item.cases, 0), minCases, maxCases), 
              color: '#fff', padding: '15px', borderRadius: '8px', marginBottom: '20px' 
            }}>
              <h3 style={{ margin: '0 0 5px 0', fontSize: '14px', fontWeight: 'normal' }}>Всего случаев</h3>
              <p style={{ margin: 0, fontSize: '32px', fontWeight: 'bold' }}>
                {Object.values(regionData.statistics).reduce((sum, item) => sum + item.cases, 0).toLocaleString('ru-RU')}
              </p>
            </div>

            <h3 style={{ color: '#495057', marginBottom: '15px' }}>Распространенные заболевания</h3>

            <div style={{ marginBottom: '30px' }}>
              {Object.entries(regionData.statistics).map(([name, item], index) => {
                const totalCases = Object.values(regionData.statistics).reduce((sum, i) => sum + i.cases, 0);
                const percentage = (item.cases / totalCases) * 100;
                return (
                  <div key={index} style={{ marginBottom: '15px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '5px' }}>
                      <span style={{ fontSize: '14px', color: '#495057' }}>{name}</span>
                      <span style={{ fontSize: '14px', fontWeight: 'bold', color: '#212529' }}>{percentage.toFixed(1)}%</span>
                    </div>
                    <div style={{ width: '100%', height: '8px', backgroundColor: '#e9ecef', borderRadius: '4px', overflow: 'hidden' }}>
                      <div style={{ width: `${percentage}%`, height: '100%', backgroundColor: getRegionColor(totalCases, minCases, maxCases), transition: 'width 0.5s ease' }} />
                    </div>
                    <span style={{ fontSize: '12px', color: '#868e96' }}>{item.cases.toLocaleString('ru-RU')} случаев</span>
                  </div>
                );
              })}
            </div>

            {/* График */}
            <h3 style={{ color: '#495057', marginBottom: '15px' }}>Статистика по заболеваниям</h3>
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={Object.entries(regionData.statistics).map(([name, item]) => ({ name, cases: item.cases }))}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" angle={-45} textAnchor="end" height={100} interval={0} style={{ fontSize: '10px' }} />
                <YAxis />
                <Tooltip />
                <Bar dataKey="cases" fill={getRegionColor(Object.values(regionData.statistics).reduce((sum, i) => sum + i.cases, 0), minCases, maxCases)} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      {/* Основная карта */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', backgroundColor: '#f8f9fa' }}>
        <div style={{ padding: '20px', backgroundColor: '#fff', boxShadow: '0 2px 4px rgba(0,0,0,0.1)', zIndex: 5 }}>
          <h1 style={{ margin: 0, color: '#212529', fontSize: '24px' }}>Заболевания беременных по регионам России</h1>
          <p style={{ margin: '5px 0 10px 0', color: '#868e96', fontSize: '14px' }}>Кликните на регион для просмотра статистики. Используйте колесико мыши для зума.</p>
          <div style={{ display: 'flex', gap: '10px', alignItems: 'center', marginTop: '15px', padding: '15px', backgroundColor: '#f8f9fa', borderRadius: '8px', flexWrap: 'wrap' }}>
            <label style={{ padding: '8px 16px', backgroundColor: '#228be6', color: '#fff', borderRadius: '4px', cursor: 'pointer', fontSize: '14px' }}>
              <input type="file" accept=".json,.geojson" onChange={handleFileUpload} style={{ display: 'none' }} />
              Загрузить GeoJSON
            </label>
            <label style={{ padding: '8px 16px', backgroundColor: '#40c057', color: '#fff', borderRadius: '4px', cursor: 'pointer', fontSize: '14px' }}>
              <input type="file" accept=".csv" onChange={handleCSVUpload} style={{ display: 'none' }} />
              Загрузить CSV
            </label>
            {loading && <span style={{ color: '#228be6', fontSize: '14px' }}>⏳ Загрузка...</span>}
            {error && <span style={{ color: '#fa5252', fontSize: '14px' }}>❌ {error}</span>}
            {geoData && <span style={{ color: '#40c057', fontSize: '14px' }}>✓ Карта загружена</span>}
          </div>
          {/* Легенда цветов */}
          <div style={{ marginTop: '15px', padding: '10px', backgroundColor: '#fff', borderRadius: '4px', border: '1px solid #dee2e6' }}>
            <div style={{ fontSize: '12px', color: '#495057', marginBottom: '5px' }}>Легенда: Мало заболеваний (зеленый) → Много заболеваний (красный)</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
              <div style={{ width: '20px', height: '20px', backgroundColor: getRegionColor(minCases, minCases, maxCases), border: '1px solid #dee2e6' }}></div>
              <span style={{ fontSize: '11px', color: '#868e96' }}>{minCases.toLocaleString('ru-RU')}</span>
              <div style={{ flex: 1, height: '20px', background: `linear-gradient(to right, ${getRegionColor(minCases, minCases, maxCases)}, ${getRegionColor(maxCases, minCases, maxCases)})`, border: '1px solid #dee2e6', margin: '0 5px' }}></div>
              <span style={{ fontSize: '11px', color: '#868e96' }}>{maxCases.toLocaleString('ru-RU')}</span>
              <div style={{ width: '20px', height: '20px', backgroundColor: getRegionColor(maxCases, minCases, maxCases), border: '1px solid #dee2e6' }}></div>
            </div>
          </div>
        </div>
        <div style={{ flex: 1, position: 'relative' }}>
          <RussiaMap onRegionClick={handleRegionClick} selectedRegion={selectedRegion} geoData={geoData} regionsData={regionsData} minCases={minCases} maxCases={maxCases} />
        </div>
      </div>
    </div>
  );
};

export default App;

# Imagem da casca, servida na raiz do domínio pelo gateway. Publicada como ghcr.io/<conta>/casca.
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ENV PORTA=3000
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/seguranca.conf /etc/nginx/seguranca.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 3000

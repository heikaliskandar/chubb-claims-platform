FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build

FROM nginx:1.31-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80

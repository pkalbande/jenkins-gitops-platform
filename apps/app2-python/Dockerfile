FROM nginx:alpine

# Copy HTML content
COPY index.html /usr/share/nginx/html/
COPY deploy.sh /usr/local/bin/

RUN chmod +x /usr/local/bin/deploy.sh

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
